package org.aggregationServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

public class ContentServer implements Runnable {
    private final String serverAddress;
    private final int serverPort;
    private final String filePath;

    private final String serverId;
    private volatile boolean isRunning;
    private LamportClock clock;

    private final ObjectMapper objectMapper;
    private static final Logger LOGGER = Logger.getLogger(ContentServer.class.getName());

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static int PUT_INTERVAL_MS = 10000; // 10 seconds between PUTs

    /**
     * Constructs a ContentServer object.
     *
     * @param serverAddress string in the format "localhost:xxxx" where x is an integer
     * @param serverPort integer representing the server's port number
     * @param filePath file path representing the file that the Content Server will read from to send as a PUT request
     */
    public ContentServer(String serverAddress, int serverPort, String filePath, String serverId) {
        // Extend for testing multiple content servers
        this.serverId = serverId;

        // Set up logging file before doing anything else
        setupLogging();

        this.filePath = filePath;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.objectMapper = new ObjectMapper();

        this.isRunning = true;
        this.clock = new LamportClock("ContentServer_" + serverId);

        LOGGER.info(String.format("[Server %s] Content Server created.\n", serverId));
    }

    /**
     * Utility function to set up logging for entity
     */
    private void setupLogging() {
        try {
            // Remove all existing handlers
            for (Handler handler : LOGGER.getHandlers()) {
                LOGGER.removeHandler(handler);
            }

            // Create contentserver-specific log file
            String logFile = String.format("runtime-files/contentserver_%s.log", serverId);
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


    public void shutdown() {
        this.isRunning = false;
        LOGGER.info(String.format("[ContentServer %s] Shutting down...\n", serverId));
    }


    @Override
    public void run() {
        try {
            // Parse the file once at startup
            String weatherData = parseFileToJson();
            LOGGER.info(String.format("[ContentServer %s] Weather data parsed successfully.\n", serverId));

            LOGGER.info(String.format("[ContentServer %s] started. Sending PUT requests every %d ms. Ctrl+C to stop.\n", serverId, PUT_INTERVAL_MS));

            // Main loop for periodic PUT requests
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    boolean success = sendPutRequest(weatherData);
                    if (success) {
                        LOGGER.info(String.format("[ContentServer %s] Weather data sent.\n", serverId));
                    } else {
                        LOGGER.severe(String.format("[ContentServer %s] Failed to send data.\n", serverId));
                    }
                    // the part that lets periodic updates occur
                    Thread.sleep(PUT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    LOGGER.info(String.format("[ContentServer %s] interrupted, shutting down...\n", serverId));
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.severe(String.format("[Server %s] Error: %s\n", serverId, e.getMessage()));
        }
    }

    /**
     * Sends a put request, by creating a non-persistent connection with the server.
     * This connection will close once a response is received.
     * @param weatherDataJSON
     * @return
     * @throws IOException
     */
    private boolean sendPutRequest(String weatherDataJSON) throws IOException {
        // Send put requests - retry for defined maximum tried if needed
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Socket socket = new Socket(serverAddress, serverPort);
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Write Header for PUT Request
                out.write("PUT /weather.json HTTP/1.1\r\n");
                out.write("User-Agent: ATOMClient/1/0\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("Content-Length: " + weatherDataJSON.length() + "\r\n");
                out.write("Lamport-Clock: " + clock.increment() + "\r\n");

                // Write body
                out.write("\r\n");
                out.write(weatherDataJSON);

                // Send
                out.flush();

                // Read response
                String statusLine = in.readLine();
                if (statusLine == null) {
                    throw new IOException("No response from server");
                }

                String[] statusParts = statusLine.split(" ", 3);
                int statusCode = Integer.parseInt(statusParts[1]);

                LOGGER.info("PUT request sent, Response Code: " + statusCode + "\n");

                // Read headers
                String line;
                int serverLamportClock = -1;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.startsWith("Lamport-Clock: ")) {
                        serverLamportClock = Integer.parseInt(line.split(": ")[1]);
                        break;
                    }
                }

                if (statusCode == 200 || statusCode == 201) {
                    clock.updateOnReceive(serverLamportClock);
                    return true;
                } else {
                    LOGGER.warning("Failed to upload weather data. Response Code: " + statusCode + "\n");
                    if (attempt < MAX_RETRIES) {
                        LOGGER.info("Retrying in " + RETRY_DELAY_MS + "ms..." + "\n");
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.severe("Error occurred during PUT request: " + e.getMessage() + "\n");
                if (attempt == MAX_RETRIES) {
                    throw new IOException("Failed to send PUT request after " + MAX_RETRIES + " attempts", e);
                }
            }
        }
        return false;
    }

    /**
     * Utility function that parses text file into JSON format to be sent in the body of the PUT request
     * @return String containing the JSON-formatted weather data
     * @throws IOException if there are file reading or JSON conversion errors
     */
    private String parseFileToJson() throws IOException {
        Map<String, Object> weatherData = new LinkedHashMap<>();
        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);  // Enable pretty printing

        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;  // Skip empty lines

                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    // Convert numeric values appropriately
                    if (key.equals("local_date_time_full")) {  // handle manually (into string instead of int)
                        weatherData.put(key, value);
                    } else if (value.matches("-?\\d+\\.\\d+")) {  // Double values
                        weatherData.put(key, Double.parseDouble(value));
                    } else if (value.matches("-?\\d+")) {  // Integer values
                        weatherData.put(key, Integer.parseInt(value));
                    } else {  // String values
                        weatherData.put(key, value);
                    }
                }
            }

            if (!weatherData.containsKey("id")) {
                throw new IOException("File is missing 'id' field. Rejecting the feed.");
            }

            // Convert map to JSON string with proper formatting
            return objectMapper.writeValueAsString(weatherData);

        } catch (NoSuchFileException e) {
            LOGGER.severe("File not found: " + filePath);
            throw new IOException("File not found: " + filePath, e);
        } catch (IOException e) {
            LOGGER.severe("Error reading file: " + e.getMessage());
            throw e;
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

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java ContentServer <server_address:port> <file_path> <PUT request interval>\n");
            return;
        }

        try {
            // Check command line arguments
            URL url = parseURL(args[0]);
            File file = new File(args[1]);
            if (!file.exists()) {
                System.out.println("Filepath does not exist: " + args[1] + "\n");
                return;
            }
            PUT_INTERVAL_MS = Integer.parseInt(args[2]);

            // Create single content server for normal use
            ContentServer contentServer = new ContentServer(url.getHost(), url.getPort(), args[1], "Main-Content-Server");

            Thread contentServerThread = new Thread(contentServer);
            contentServerThread.start();

            // Wait for Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                contentServer.shutdown();
                try {
                    contentServerThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));


            contentServerThread.join();

        } catch (MalformedURLException e) {
            LOGGER.severe("Invalid server URL format. Please use a valid format like 'http://servername:port'\n");
            System.exit(-1);
        } catch (URISyntaxException e) {
            LOGGER.severe("Unable to create URI as provided server URL violates RFC 2396: " + e.getMessage() + "\n");
            System.exit(-1);
        } catch (NumberFormatException e) {
            LOGGER.severe("Unable to parse <PUT request interval> into an integer: " + e.getMessage() + "\n");
            System.exit(-1);
        } catch (InterruptedException e) {
            LOGGER.severe("Interrupted while joining the contentServer threads: " + e.getMessage() + "\n");
            System.exit(-1);
        }
    }
}
