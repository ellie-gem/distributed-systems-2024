package org.aggregationServer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import java.util.logging.*;
import com.fasterxml.jackson.databind.JsonNode;

public class GETClient implements Runnable{

    private final String serverAddress;
    private final int serverPort;

    private LamportClock clock;
    private final ObjectMapper objectMapper;
    private static final Logger LOGGER = Logger.getLogger(GETClient.class.getName());

    private volatile boolean isRunning;
    private final String clientId; // To identify different test clients
    private final Queue<String> requestQueue; // For storing stations IDs to request

    /**
     * Constructs a GETClient for the sole purpose of GET-ting weather data from the server.
     * A client socket, buffered reader and writer are created upon object creation.
     * Creating a client socket
     * @param serverAddress url pointing to the address that client wants to connect to
     * @param serverPort an integer specifying server's port number
     */
    public GETClient(String serverAddress, int serverPort, String clientId) throws IOException {
        // Mainly for testing multiple clients
        this.clientId = clientId;

        // Set up logging file before doing anything else
        setupLogging();

        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        this.clock = new LamportClock("GETClient_" + clientId);
        this.objectMapper = new ObjectMapper();

        // Mainly for testing multiple clients
        this.isRunning = true;
        this.requestQueue = new ConcurrentLinkedQueue<>();

        LOGGER.info(String.format("[Client %s] Initialized for server %s:%d\n", clientId, serverAddress, serverPort));
    }

    /**
     * Add a station ID to be requested by this client
     */
    public void addRequest(String stationId) {
        requestQueue.offer(stationId);
    }

    /**
     * Gracefully stop the client
     */
    public void shutdown() {
        isRunning = false;
        LOGGER.info(String.format("[Client %s] Shutting down...\n", clientId));
    }

    /**
     * Set up logging to log file and to terminal for better visibility of events
     */
    private void setupLogging() {
        try {
            // Remove all existing handlers
            for (Handler handler : LOGGER.getHandlers()) {
                LOGGER.removeHandler(handler);
            }

            // Create file handler with clientId in filename
            String logFile = String.format("runtime-files/getclient_%s.log", clientId);
            FileHandler fileHandler = new FileHandler(logFile, true);
            fileHandler.setFormatter(new SimpleFormatter());

            // Create console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());

            // Add both handlers to the logger
            LOGGER.addHandler(fileHandler);
            LOGGER.addHandler(consoleHandler);

            // Set the logger level
            LOGGER.setLevel(Level.ALL);

            // Prevent logging from being passed to parent handlers
            LOGGER.setUseParentHandlers(false);
        } catch (IOException e) {
            System.err.println("Failed to set up logging: " + e.getMessage() + "\n");
            System.exit(-1);
        }
    }


    /**
     * Utility method to parse the server URL. Appends URL with "http://" if argument does not begin with that
     * @param serverURL a string representing the aggregated server's host address
     * @return a validated URL
     * @throws MalformedURLException when the parsed URL does not comply with required syntax of associated protocol
     */
    private static URL parseURL(String serverURL) throws MalformedURLException, URISyntaxException {
        if (!serverURL.startsWith("http://")) {
            serverURL = "http://" + serverURL;
        }

        // Have to construct URI and convert to URL this way as constructing URL itself is deprecated
        return new URI(serverURL).toURL();
    }

    /**
     * Sends a get request to the aggregated server (assuming Keep-Alive to maintain persistent connections)
     * Maintain persistent connection for simplicity in testing
     * @param stationID a string representing the weather station content to be queried
     */
    public void sendGetRequest(String stationID) {
        try (Socket socket = new Socket(serverAddress, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            // Send GET request
            String request = String.format("GET /weather_data.json?station-id=%s&lamport-clock=%d HTTP/1.1", stationID, clock.increment());
            out.write(request);
            out.newLine();
            out.flush();
            LOGGER.info("Sent GET request for station: " + stationID + " (Lamport clock: " + clock.getValue() + ")\n");

            // Read response
            String line;
            int contentLength = 0;
            int receivedLamportClock = -1;
            int statusCode = 0;

            // Read headers
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("HTTP/1.1")) {
                    statusCode = Integer.parseInt(line.split(" ")[1].trim());
                } else if (line.startsWith("Lamport-Clock:")) {
                    receivedLamportClock = Integer.parseInt(line.split(":")[1].trim());
                } else if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            // Read the JSON response body
            String jsonResponse = readJsonResponse(in, contentLength);

            // Update lamport clock
            clock.updateOnReceive(receivedLamportClock);
            LOGGER.info("Received response with status:" + statusCode + ". New Lamport clock: " + clock.getValue() + "\n");

            displayWeatherData(jsonResponse);

        } catch (IOException e) {
            LOGGER.severe("Error during GET request: " + e.getMessage() + "\n");
        }
    }

    /**
     * abstracted away the part where we read in JSON response
     * @param in buffered reader
     * @param contentLength length of response body from server
     * @return the actual json response
     * @throws IOException when error occurs on I/O
     */
    private String readJsonResponse(BufferedReader in, int contentLength) throws IOException {
        if (contentLength <= 0) {
            LOGGER.warning("Invalid content length: " + contentLength + "\n");
            return "";
        }

        char[] buffer = new char[contentLength];
        int totalRead = 0;

        // Only read in specified amount of characters
        while (totalRead < contentLength) {
            int read = in.read(buffer, totalRead, contentLength - totalRead);
            if (read == -1) {
                LOGGER.warning("End of stream reached before reading all content. Read " + totalRead + " out of " + contentLength + " chars." + "\n");
                break;
            }
            totalRead += read;
        }

        return new String(buffer, 0, totalRead);
    }

    /**
     * Make jsonResponse string into JSONNode object and then print out the fields and entries
     * @param jsonResponse json response in string format
     */
    public void displayWeatherData(String jsonResponse) {
        try {
            // Parse the JSON string into a JsonNode
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            // Validate that the JSON is not empty
            if (jsonNode.isEmpty()) {
                LOGGER.warning("Received JSON is empty\n");
                return;
            }

            LOGGER.info("Received weather data:\n");
            StringBuilder weatherInfo = new StringBuilder("Weather Data:\n\n");

            // Iterate through all fields in the JsonNode
            Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                // Append each field and its value to the StringBuilder
                weatherInfo.append(fieldName).append(": ");
                if (fieldValue.isTextual()) {
                    weatherInfo.append(fieldValue.asText());
                } else if (fieldValue.isNumber()) {
                    weatherInfo.append(fieldValue.asText());  // Use asText() to preserve decimal places
                } else if (fieldValue.isNull()) {
                    weatherInfo.append("null");
                } else {
                    weatherInfo.append(fieldValue.toString());
                }
                weatherInfo.append("\n");
            }

            // Log the complete weather information
            LOGGER.info(weatherInfo.toString() + "\n");

        } catch (IOException e) {
            LOGGER.severe("Error parsing weather data: " + e.getMessage() + "\n");
        }
    }


    @Override
    public void run() {
        LOGGER.info("[Client " + clientId + "] Starting...\n");

        while (isRunning) {
            String stationId = requestQueue.poll();

            if (stationId != null) {
                // Check regex for station id
                if (stationId.matches("IDS\\d{5}")) {
                    LOGGER.info(String.format("[Client %s] Requesting data for station: %s\n", clientId, stationId));
                    sendGetRequest(stationId);
                } else {
                    LOGGER.warning(String.format("[Client %s] Invalid station ID: %s\n", clientId, stationId));
                }
            } else {
                // No requests, sleep briefly
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        LOGGER.info(String.format("[Client %s] Stopped processing requests.\n", clientId));
    }


    /**
     * Starts the program, parses arguments to get server host address and port number.
     * Creates GETClient object and waits to read from stdin for stationID that it wants to get data from.
     * Checks validity of stationID using regex before sending GET request to server
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <server_address:port>\n");
            return;
        }

        try {
            String serverURL = args[0];
            URL url = parseURL(serverURL);

            // Create single client for terminal use
            GETClient client = new GETClient(url.getHost(), url.getPort(), "Terminal-Client");
            Thread clientThread = new Thread(client);
            clientThread.start();

            Scanner scanner = new Scanner(System.in);

            // Instead of doing a while loop, try a functional programming style (for fun)
            Stream.generate(scanner::nextLine)
                    .takeWhile(input -> !input.equalsIgnoreCase("exit"))
                    .forEach(stationID -> {
                        if (stationID.matches("IDS\\d{5}")) {
                            client.addRequest(stationID);
                        } else {
                            LOGGER.warning("Invalid station ID entered: " + stationID +
                                    "\nPlease enter in the format 'IDSxxxxx' where x is a digit.\n");
                        }
                    });

            // Shutdown client
            client.shutdown();
            clientThread.join();

        } catch (MalformedURLException e) {
            LOGGER.severe("Invalid server URL format. Please use a valid format like 'http://servername:port'\n");
        } catch (URISyntaxException e) {
            LOGGER.severe("Unable to create URI as provided server URL violates RFC 2396: " + e.getMessage() + "\n");
        } catch (IOException e) {
            LOGGER.severe("Could not create client: " + e.getMessage() + "\n");
        } catch (InterruptedException e) {
            LOGGER.severe("Client thread interrupted: " + e.getMessage() + "\n");
            Thread.currentThread().interrupt();
        }
    }
}
