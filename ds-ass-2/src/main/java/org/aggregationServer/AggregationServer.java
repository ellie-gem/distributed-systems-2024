package org.aggregationServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AggregationServer {

    private static final Logger LOGGER = Logger.getLogger(AggregationServer.class.getName());
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, WeatherData> weatherData;
    private ConcurrentHashMap<String, Long> lastUpdateTimes;

    private final String storageFile = "weather_data.json";
    private ObjectMapper objectMapper;
    private ScheduledExecutorService scheduler;
    private ExecutorService threadPool;
    private volatile boolean isRunning;

    private ExecutorService dataStorageExecutor = null;
    private AtomicInteger lamportClock;
    private AtomicBoolean isDirty;

    private static final long EXPIRATION_TIME_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * Constructs a ServerSocket bound to the specified port (main entry point) upon starting the aggregated server
     * @param port an integer specifying port number
     * @throws IOException
     */
    public AggregationServer(int port) throws IOException  {
        try {
            this.serverSocket = new ServerSocket(port);
            this.isRunning = true;

            this.weatherData = new ConcurrentHashMap<>();
            this.lastUpdateTimes = new ConcurrentHashMap<>();

            this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            this.scheduler = Executors.newScheduledThreadPool(1);
            this.threadPool = Executors.newCachedThreadPool();
            this.dataStorageExecutor = Executors.newSingleThreadExecutor();

            this.lamportClock = new AtomicInteger(0);
            this.isDirty = new AtomicBoolean(false);

            setupLogging();

            loadDataFromFile(); // Is useful for when server restarts after a crash

            startPeriodicSave();

            startExpirationChecker();

            // Register a shutdown hook to close the resources when the application is interrupted (ie Ctrl+C from terminal)
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));

            LOGGER.info("Aggregation Server started and listening on port " + port);

        } catch (IOException e) {
            System.out.println("[AS]: Could not listen on port: " + port);
            e.printStackTrace();
        }
    }

    /**
     * Sets up logging for debugging purposes
     */
    private void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
        } catch (IOException e) {
            LOGGER.severe("Failed to set up file logging: " + e.getMessage());
        }
    }

    /**
     * Starts server and continuously accepts incoming requests.
     */
    public void start() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("New client connected from: " + clientSocket.getInetAddress().getHostAddress());
                threadPool.submit(new RequestHandler(clientSocket, this));
            } catch (IOException e) {
                if (isRunning) {
                    LOGGER.severe("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stops server and closes all connections and threads.
     */
    public void stopServer() {
        isRunning = false;
        scheduler.shutdown();
        threadPool.shutdown();
        dataStorageExecutor.shutdown();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

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
            LOGGER.severe("Could not close server socket: " + e.getMessage());
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            threadPool.shutdownNow();
            dataStorageExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            saveDataToFile();
            LOGGER.info("Aggregation Server stopped.");
        }
    }

    /**
     * Loads data from .json storage file into the concurrent hashmap upon a clean start / server crash
     */
    private void loadDataFromFile() {
        try {
            File file = new File(storageFile);
            if (file.exists()) {
                WeatherData[] dataArray = objectMapper.readValue(file, WeatherData[].class);
                for (WeatherData data : dataArray) {
                    weatherData.put(data.getId(), data);
                }
                LOGGER.info("Loaded " + dataArray.length + " weather data entries from file.");
            }
        } catch (IOException e) {
            LOGGER.warning("Error loading data from file: " + e.getMessage());
        }
    }

    /**
     * Run expiry checks across concurrent hash maps every second
     */
    private void startExpirationChecker() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            boolean removed = false;
            for (String stationId : lastUpdateTimes.keySet()) {
                Long lastUpdateTime = lastUpdateTimes.get(stationId);
                if (lastUpdateTime != null && (now - lastUpdateTime) > EXPIRATION_TIME_MS) {
                    if (weatherData.remove(stationId) != null) {
                        lastUpdateTimes.remove(stationId);
                        removed = true;
                        LOGGER.info("Removed expired weather data for station: " + stationId);
                    }
                }
            }
            if (removed) {
                isDirty.set(true);
                LOGGER.info("Removed expired weather data entries");
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


    /**
     * A scheduler that routinely checks every 1 minute to perform save to storage
     * This is required for maintaining persistent data.
     */
    private void startPeriodicSave() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isDirty.getAndSet(false)) {
                saveDataToFile();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Save data to temp file only if concurrent hash map has been updated (checked by startPeriodicSave)
     */
    private void saveDataToFile() {
        dataStorageExecutor.submit(() -> {
            Path tempFilePath = Paths.get("weather_data.tmp");
            Path storageFilePath = Paths.get(storageFile);

            try {
                // Write to temp file
                objectMapper.writeValue(tempFilePath.toFile(), weatherData.values());

                // Atomic move: replace the old file with the new one
                Files.move(tempFilePath, storageFilePath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                LOGGER.info("Saved " + weatherData.size() + " weather data entries to file.");
            } catch (IOException e) {
                LOGGER.warning("Error saving data to file: " + e.getMessage());
            } finally {
                // Ensure temp file is deleted if it still exists (e.g., if move failed)
                try {
                    Files.deleteIfExists(tempFilePath);
                } catch (IOException e) {
                    LOGGER.warning("Failed to delete temporary file: " + e.getMessage());
                }
            }
        });
    }

    // Below methods are for printing out json data after receiving response back from server
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
        weatherData.put(stationID, data);
        lastUpdateTimes.put(stationID, System.currentTimeMillis());
        isDirty.set(true);
    }

    /**
     * uses the updateAndGet method of AtomicInteger to calculate the new value based on the current value.
     * handles compare-and-set operation internally, ie if the set operation fails
     * (because another thread changed the value in the meantime), it loops and tries again.
     * @param incomingClock from client
     * @return the new set lamport clock
     */
    public int updateLamportClock(int incomingClock) {
        return lamportClock.updateAndGet(current -> Math.max(current, incomingClock) + 1);
    }

    /**
     * Gets servers lamport clock
     * @return an integer representing lamport clock of aggregation server
     */
    public int getLamportClock() {
        return lamportClock.get();
    }

    /**
     * Main function to start aggregation server
     * @param args command line arguments when starting the aggregated server
     * @throws InstantiationException when unable to instantiate the aggregated server object
     */
    public static void main(String[] args) throws Exception {
        // Default port
        int startingPort = 4567;

        if (args.length == 1) {
            startingPort = Integer.parseInt(args[0]);
        }

        try {
            AggregationServer server = new AggregationServer(startingPort);
            server.start();
        } catch (IOException e) {
            LOGGER.severe("Could not start server: " + e.getMessage());
        }
    }

}
