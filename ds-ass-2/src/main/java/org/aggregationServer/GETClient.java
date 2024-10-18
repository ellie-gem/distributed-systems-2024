package org.aggregationServer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.logging.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;

public class GETClient {
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private AtomicInteger lamportClock;
    private ObjectMapper objectMapper;
    private static final Logger LOGGER = Logger.getLogger(GETClient.class.getName());

    /**
     * Constructs a GETClient for the sole purpose of GET-ting weather data from the server.
     * A client socket, buffered reader and writer are created upon object creation.
     * Creating a client socket
     * @param serverAddress url pointing to the address that client wants to connect to
     * @param serverPort an integer specifying server's port number
     */
    public GETClient(String serverAddress, int serverPort) throws IOException {

        try {
            this.socket = new Socket(serverAddress, serverPort);
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.lamportClock = new AtomicInteger(0);
            this.objectMapper = new ObjectMapper();

            setupLogging();

            // Register a shutdown hook to close the resources when the application is interrupted (ie Ctrl+C from terminal)
            Runtime.getRuntime().addShutdownHook(new Thread(this::closeEverything));

            LOGGER.info("GETClient connected to " + serverAddress + ":" + serverPort);

        } catch (UnknownHostException e) {
            LOGGER.severe("Unknown host: " + e.getMessage());
            closeEverything();
        } catch (IOException e) {
            LOGGER.severe("Unable to create socket for GETClient: " + e.getMessage());
            closeEverything();
        }
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

            // Create file handler
            FileHandler fileHandler = new FileHandler("getclient.log", true);
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
            System.err.println("Failed to set up logging: " + e.getMessage());
        }
    }

    /**
     * Closes the client handler's socket, its buffered reader and buffered writer.
     */
    private void closeEverything() {
        try {
            if (this.socket != null) this.socket.close();
            if (this.bufferedReader != null) this.bufferedReader.close();
            if (this.bufferedWriter != null) this.bufferedWriter.close();
            LOGGER.info("GETClient resources closed");
        } catch (IOException e) {
            LOGGER.severe("Unable to close socket for GETClient: " + e.getMessage());
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
     *
     * @param stationID
     */
    public void sendGetRequest(String stationID) {
        try {
            int currentClock = lamportClock.getAndIncrement();  ////// do you need to increment before sending???
            String request = String.format("GET /weather.json?station-id=%s&lamport-clock=%d HTTP/1.1", stationID, currentClock);
            bufferedWriter.write(request);
            bufferedWriter.newLine();
            bufferedWriter.flush();

            LOGGER.info("Sent GET request for station: " + stationID + " with Lamport clock: " + currentClock);

            // Read headers
            String line;
            int contentLength = 0;
            int newLamportClock = -1;
            while ((line = bufferedReader.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                } else if (line.startsWith("Lamport-Clock:")) {
                    newLamportClock = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            // Read the JSON body
            String jsonResponse = readJsonResponse(contentLength);

            lamportClock.set(Math.max(lamportClock.get(), newLamportClock));
            LOGGER.info("Received response. New Lamport clock: " + lamportClock.get());

            displayWeatherData(jsonResponse);

        } catch (IOException e) {
            LOGGER.severe("Unable to send GET request: " + e.getMessage());
        }
    }

    /**
     * abstracted away the part where we read in JSON response
     * @param contentLength length of response body from server
     * @return the actual json response
     * @throws IOException when error occurs on I/O
     */
    private String readJsonResponse(int contentLength) throws IOException {
        if (contentLength <= 0) {
            LOGGER.warning("Invalid content length: " + contentLength);
            return "";
        }

        CharBuffer buffer = CharBuffer.allocate(contentLength);
        int totalRead = 0;
        while (totalRead < contentLength) {
            int charsRead = bufferedReader.read(buffer);
            if (charsRead == -1) {
                LOGGER.warning("End of stream reached before reading all content. Read " + totalRead + " out of " + contentLength + " chars.");
                break;
            }
            totalRead += charsRead;
        }

        buffer.flip();
        String jsonResponse = buffer.toString();

        if (totalRead < contentLength) {
            LOGGER.warning("Incomplete read: expected " + contentLength + " chars, got " + totalRead);
        } else if (totalRead > contentLength) {
            LOGGER.warning("Read more data than expected: " + totalRead + " vs " + contentLength);
            jsonResponse = jsonResponse.substring(0, contentLength);
        }

        return jsonResponse;
    }

    /**
     * Make jsonResponse string into JSONNode object and then print out the fields and entries
     * @param jsonResponse
     */
    public void displayWeatherData(String jsonResponse) {
        try {
            // Parse the JSON string into a JsonNode
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            // Validate that the JSON is not empty
            if (jsonNode.isEmpty()) {
                LOGGER.warning("Received JSON is empty");
                return;
            }

            LOGGER.info("Received weather data:");
            StringBuilder weatherInfo = new StringBuilder("Weather Data:\n");

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
            LOGGER.info(weatherInfo.toString());

        } catch (IOException e) {
            LOGGER.severe("Error parsing weather data: " + e.getMessage());
        }
    }


    /**
     * Starts the program, parses arguments to get server host address and port number.
     * Creates GETClient object and waits to read from stdin for stationID that it wants to get data from.
     * Checks validity of stationID using regex before sending GET request to server
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GETClient <serverURL>");
            return;
        }

        try {
            String serverURL = args[0];
            URL url = parseURL(serverURL);
            GETClient client = new GETClient(url.getHost(), url.getPort());

            Scanner scanner = new Scanner(System.in);

            // Instead of doing a while loop, try a functional programming style (for fun)
            Stream.generate(scanner::nextLine)
                    .takeWhile(input -> !input.equalsIgnoreCase("exit"))
                    .forEach(stationID -> {
                        if (stationID.matches("IDS\\d{5}")) {
                            LOGGER.info("Requesting data for station: " + stationID);
                            client.sendGetRequest(stationID);
                        } else {
                            LOGGER.warning("Invalid station ID entered: " + stationID + "Please enter in the format 'IDSxxxxx' where x is a digit.");
                        }
                    });

//            client.closeEverything();

        } catch (MalformedURLException e) {
            LOGGER.severe("Invalid server URL format. Please use a valid format like 'http://servername:port'");
        } catch (URISyntaxException e) {
            LOGGER.severe("Unable to create URI as provided server URL violates RFC 2396: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.severe("Could not create client: " + e.getMessage());
        }
    }
}
