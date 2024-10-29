package org.aggregationServer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;

public class AggregationServer {

    private static final Logger LOGGER = Logger.getLogger(AggregationServer.class.getName());
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, WeatherData> weatherData;
    private ConcurrentHashMap<String, Long> lastUpdateTimes;

    private final String storageFile = "backup-files/weather_data_backup.json";
    private ObjectMapper objectMapper;
    private ScheduledExecutorService scheduler;
    private ExecutorService threadPool;
    private volatile boolean isRunning;

    private ExecutorService dataStorageExecutor = null;
    protected LamportClock clock;
    private AtomicBoolean isDirty;

    private PriorityBlockingQueue<Request> requestQueue;
    private RequestProcessor requestProcessor;
    private Thread processorThread;

    private static final long EXPIRATION_TIME_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * Wrapper class for storing nested JSON (POJO method)
     */
    private static class WeatherStation {
        @JsonProperty("last-updated")
        private String lastUpdated;
        @JsonProperty("last-lamport")
        private int lastLamport;
        private WeatherData data;

        // Default constructor for Jackson
        public WeatherStation() {}

        public WeatherStation(WeatherData data, String lastUpdated, int lastLamport) {
            this.data = data;
            this.lastUpdated = lastUpdated;
            this.lastLamport = lastLamport;
        }

        // Getters and Setters
        public String getLastUpdated() {
            return lastUpdated;
        }
        public void setLastUpdated(String lastUpdated) {
            this.lastUpdated = lastUpdated;
        }
        public WeatherData getData() {
            return data;
        }
        public void setData(WeatherData data) {
            this.data = data;
        }
        public int getLastLamport() {
            return lastLamport;
        }
        public void setLastLamport(int lastLamport) {
            this.lastLamport = lastLamport;
        }
    }

    /**
     * Constructs a ServerSocket bound to the specified port (main entry point) upon starting the aggregated server
     * @param port an integer specifying port number
     * @throws IOException
     */
    public AggregationServer(int port, boolean log) throws IOException  {
        // Set up logging file before doing anything else
        if (log) setupLogging();

        try {
            this.serverSocket = new ServerSocket(port);
            this.isRunning = true;

            this.weatherData = new ConcurrentHashMap<>();
            this.lastUpdateTimes = new ConcurrentHashMap<>();

            this.objectMapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            this.scheduler = Executors.newScheduledThreadPool(1);
            this.threadPool = Executors.newCachedThreadPool();
            this.dataStorageExecutor = Executors.newSingleThreadExecutor();

            this.clock = new LamportClock("aggregationserver");
            this.isDirty = new AtomicBoolean(false);


            // For serving requests based on lamport clock
            this.requestQueue = new PriorityBlockingQueue<>(
                    11,
                    Comparator.comparingInt(Request::getLamportClock)
            );
            this.requestProcessor = new RequestProcessor();

            loadDataFromFile(); // Is useful for when server restarts after a crash

            // Register a shutdown hook to close the resources when the application is interrupted (ie Ctrl+C from terminal)
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));

            LOGGER.info("Aggregation Server started and listening on port " + port + "\n");

        } catch (IOException e) {
            System.out.println("[AS]: Could not listen on port: " + port + "\n");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Sets up logging for debugging purposes
     */
    private void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("runtime-files/aggregationserver.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("Failed to set up logging: " + e.getMessage() + "\n");
            System.exit(-1);
        }
    }

    /**
     * Starts server and continuously accepts incoming requests.
     * The logger will print out the client's address + it's port number
     */
    public void start() {
        // Start request processor thread explicitly here when starting server
        this.processorThread = new Thread(requestProcessor);
        this.processorThread.start();

        startPeriodicSave();
        startExpirationChecker();

        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("New client connected from: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort() + "\n");
                threadPool.submit(new RequestHandler(clientSocket, this));
            } catch (IOException e) {
                if (isRunning) {
                    LOGGER.severe("Error accepting client connection: " + e.getMessage() + "\n");
                }
            }
        }
    }

    /**
     * Stops server and closes all connections and threads.
     */
    public void stopServer() {
        LOGGER.info("Shutting down Aggregation Server...\n");
        isRunning = false;

        try {
            // 1. First shutdown request processor and wait
            LOGGER.info("Shutting down request processor...\n");
            requestProcessor.shutdown();
            if (processorThread != null) {
                processorThread.interrupt();  // Interrupt if blocked on queue.take()
                try {
                    processorThread.join(5000);  // Wait up to 5 seconds
                    LOGGER.info("Request processor shutdown complete.\n");
                } catch (InterruptedException e) {
                    LOGGER.warning("Request processor shutdown interrupted.\n");
                    Thread.currentThread().interrupt();
                }
            }

            // 2. Save final state if needed
            if (isDirty.get()) {
                try {
                    // Create map for creating JSON object easier
                    Map<String, WeatherStation> saveData = new HashMap<>();
                    for (Map.Entry<String, WeatherData> entry : weatherData.entrySet()) {
                        String stationId = entry.getKey();
                        WeatherStation station = new WeatherStation(
                                entry.getValue(),
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                                clock.getValue()
                        );
                        saveData.put(stationId, station);
                    }

                    // Write directly to storage file for final save
                    objectMapper.writeValue(new File(storageFile), saveData);
                    LOGGER.info("Final save: " + saveData.size() + " weather stations saved to file.\n");
                } catch (IOException e) {
                    LOGGER.warning("Error during final save: " + e.getMessage() + "\n");
                }
            }

            // 3. Shutdown executors
            scheduler.shutdown();
            threadPool.shutdown();
            dataStorageExecutor.shutdown();

            // 4. Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // 5. Wait for executors to terminate
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            if (!dataStorageExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dataStorageExecutor.shutdownNow();
            }

        } catch (IOException e) {
            LOGGER.severe("Could not close server socket: " + e.getMessage() + "\n");
        } catch (InterruptedException e) {
            LOGGER.warning("Executor services shutdown interrupted.\n");
            scheduler.shutdownNow();
            threadPool.shutdownNow();
            dataStorageExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            LOGGER.info("Aggregation Server stopped.\n");
        }
    }

    /**
     * Loads data from .json storage file into the concurrent hashmapS upon a clean start / server crash
     */
    private void loadDataFromFile() {
        try {
            File file = new File(storageFile);
            if (file.exists() && file.length() > 0) {
                Map<String, WeatherStation> stations = objectMapper.readValue(file,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, WeatherStation.class));

                for (Map.Entry<String, WeatherStation> entry : stations.entrySet()) {
                    String stationId = entry.getKey();
                    WeatherStation station = entry.getValue();

                    // Load weather data
                    weatherData.put(stationId, station.getData());

                    // Set last update time to current time
                    lastUpdateTimes.put(stationId, System.currentTimeMillis());

                    // Update Lamport clock if needed
                    clock.restoreFromSaved(station.getLastLamport());
                }

                LOGGER.info("Loaded " + stations.size() + " weather stations from file. Current Lamport clock: " + clock.getValue() + "\n");

            } else {
                LOGGER.info("File does not exist or is empty: " + storageFile + "\n");
            }
        } catch (IOException e) {
            LOGGER.warning("Error loading data from file: " + e.getMessage() + "\n");
        }
    }

    /**
     * Run expiry checks across concurrent hash maps every second
     */
    public void startExpirationChecker() {
        LOGGER.info("Started expiration checker\n");
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            boolean removed = false;
            for (String stationId : lastUpdateTimes.keySet()) {
                Long lastUpdateTime = lastUpdateTimes.get(stationId);
                if (lastUpdateTime != null && (now - lastUpdateTime) > EXPIRATION_TIME_MS) {
                    if (weatherData.remove(stationId) != null) {
                        lastUpdateTimes.remove(stationId);
                        removed = true;
                        LOGGER.info("Removed expired weather data for station: " + stationId + "\n");
                    }
                }
            }
            if (removed) {
                isDirty.set(true);
                LOGGER.info("Removed expired weather data entries.\n");
            }
        }, 0, 30, TimeUnit.SECONDS);
    }


    /**
     * A scheduler that routinely checks every 1 minute to perform save to storage
     * This is required for maintaining persistent data.
     */
    private void startPeriodicSave() {
        scheduler.scheduleAtFixedRate(() -> {
            LOGGER.info("Started periodic save to storage\n");
            if (isDirty.getAndSet(false)) {
                saveDataToFile();
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    /**
     * Save data to temp file only if concurrent hash map has been updated (checked by startPeriodicSave)
     * Saves to temp file so that if server fails during saving to either temp or main storage file, either exists
     */
    private void saveDataToFile() {
        LOGGER.info("Saving data to file (periodic save)\n");
        dataStorageExecutor.submit(() -> {
            Path tempFilePath = Paths.get("backup-files/weather_data.tmp");
            Path storageFilePath = Paths.get(storageFile);

            try {
                // Create a map of stations ->
                Map<String, WeatherStation> stations = new HashMap<>();
                for (Map.Entry<String, WeatherData> entry : weatherData.entrySet()) {
                    String stationId = entry.getKey();
                    WeatherData data = entry.getValue();
                    String lastUpdateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

                    // Create WeatherStation object (POJO) for parsing as JSON
                    WeatherStation station = new WeatherStation(data, lastUpdateTime, clock.getValue());

                    stations.put(stationId, station);
                }

                // Write weatherData concurrent hash map to temp file
                objectMapper.writeValue(tempFilePath.toFile(), stations);

                // Atomic move: replace the old file with the new one
                Files.move(tempFilePath, storageFilePath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                LOGGER.info("Saved " + weatherData.size() + " weather data entries to file.\n");
            } catch (IOException e) {
                LOGGER.warning("Error saving data to file: " + e.getMessage() + "\n");
            }
        });

        LOGGER.info("Finish saving data to file (periodic save)\n");
    }


    /**
     * Gets the weather data from the concurrent hash map by accessing station ID
     * @param stationID a string representing weather station's ID
     * @return the weather data in WeatherData class object
     */
    public WeatherData getWeatherData(String stationID) {
        return weatherData.get(stationID);
    }


    /**
     * Updates the weather data in concurrent hash map,
     * and sets the dirty flag to true so that the next interval check of save to file will occur
     * @param stationID
     * @param data
     */
    public void updateWeatherData(String stationID, WeatherData data) {
        LOGGER.info("Adding weather data into concurrent hashmap and updating last communicated time\n");
        weatherData.put(stationID, data);
        lastUpdateTimes.put(stationID, System.currentTimeMillis());
        isDirty.set(true);

        System.out.println("weatherData size:" + weatherData.size() + "\n");
        System.out.println("lastUpdated size:" + weatherData.size() + "\n");
    }


    /**
     * Clears the map (used for tests)
     */
    public void clearTestData() {
        weatherData.clear();
        lastUpdateTimes.clear();
        isDirty.set(true);
    }

    public ServerSocket getServerSocket() {
        return this.serverSocket;
    }


    /**
     * Main function to start aggregation server
     * @param args command line arguments when starting the aggregated server
     * @throws IOException when unable to instantiate and start the aggregated server object
     */
    public static void main(String[] args) throws IOException {
        // Default port
        int startingPort = 4567;

        if (args.length == 1) {
            startingPort = Integer.parseInt(args[0]);
        }

        try {
            AggregationServer server = new AggregationServer(startingPort, true);
            server.start();
        } catch (IOException e) {
            LOGGER.severe("Could not start server: " + e.getMessage() + "\n");
        }
    }


    /**
     * Method to add incoming request to queue
     * @param request
     */
    public void queueRequest(Request request) {
        requestQueue.offer(request);
    }


    /**
     * Class to process requests in order based on Lamport clock
     */
    protected class RequestProcessor implements Runnable {
        private volatile boolean isRunningRequestProcessor = true;

        @Override
        public void run() {
            while (isRunningRequestProcessor) {
                try {
                    Request request = requestQueue.take();

                    // If PUT request, add into maps
                    if ("PUT".equals(request.getType())) {
                        WeatherData weatherData = objectMapper.readValue(
                                request.getContent(),
                                WeatherData.class
                        );
                        // Update maps
                        updateWeatherData(weatherData.getId(), weatherData);
                        // Complete future response
                        request.getResponse().complete("");

                    } else if ("GET".equals(request.getType())) {
                        // If GET request, query map
                        WeatherData data = getWeatherData(request.getStationId());

                        if (data == null) {
                            // complete with empty string instead of null
                            request.getResponse().complete("");
                        } else {
                            String jsonResponse = objectMapper.writeValueAsString(data);
                            request.getResponse().complete(jsonResponse);
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error processing request: " + e.getMessage());
                }
            }
        }

        public void shutdown() {
            isRunningRequestProcessor = false;
        }
    }

}