package org.aggregationServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.*;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final AggregationServer aggregationServer;
    private BufferedReader in;
    private BufferedWriter out;
    private final ObjectMapper objectMapper;
    private static final Logger LOGGER = Logger.getLogger(RequestHandler.class.getName());

    /**
     * Constructs a request handler
     * @param clientSocket a socket (passed to request handler from accepting the connection request)
     *                     that is used to communicate with the client
     * @throws IOException when error occur while creating buffered reader and writer
     */
    public RequestHandler(Socket clientSocket, AggregationServer aggServer) throws IOException {
        this.clientSocket = clientSocket;
        this.aggregationServer = aggServer;
        this.objectMapper = new ObjectMapper();

        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            LOGGER.info("RequestHandler created for Client: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort() + "\n");
        } catch (IOException e) {
            LOGGER.severe("Error creating I/O streams: " + e.getMessage() + "\n");
            closeConnection();
        }
    }

    /**
     * Starts the handler when a connection is made between client and server
     */
    @Override
    public void run() {
        try {
            while (!clientSocket.isClosed()) {  // Keep reading requests while socket is open
                String requestLine = in.readLine();

                if (requestLine != null && !requestLine.isEmpty()) {
                    if (requestLine.startsWith("GET")) {
                        handleGetRequest(requestLine);
                    } else if (requestLine.startsWith("PUT")) {
                        handlePutRequest();
                    } else {
                        sendResponse(null, 400, aggregationServer.clock.increment());
                    }
                }
            }

        } catch (IOException e) {
            LOGGER.warning("Error handling request: " + e.getMessage() + "\n");
            try {
                sendResponse(null, 500, aggregationServer.clock.increment());
            } catch (IOException ioException) {
                LOGGER.severe("Failed to send error response: " + ioException.getMessage() + "\n");
            }
        } finally {
            closeConnection();
        }
    }

    /**
     * Parses the GET request and then queries the hashmap
     * @throws IOException when error occur during processing GET request
     */
    private void handleGetRequest(String requestLine) throws IOException {
        try {
            // Parse
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                sendResponse(null, 400, aggregationServer.clock.increment());
                return;
            }

            String url = requestParts[1];
            String[] urlParts = url.split("\\?");
            if (urlParts.length < 2 || !urlParts[0].equals("/weather_data.json")) {
                sendResponse(null, 400, aggregationServer.clock.increment());
                return;
            }

            // Parse query parameters (stationID=<id>)
            String queryParams = urlParts[1];
            String[] queryParamsArray = queryParams.split("&");
            String stationID = null;
            int receivedLamportClock = -1;

            for (String param : queryParamsArray) {
                if (param.startsWith("station-id=")) {
                    stationID = param.split("=")[1];
                } else if (param.startsWith("lamport-clock=")) {
                    try {
                        receivedLamportClock = Integer.parseInt(param.split("=")[1]);
                    } catch (NumberFormatException e) {
                        sendResponse(null, 400, aggregationServer.clock.increment());
                        return;
                    }
                }
            }

            if (stationID == null || receivedLamportClock == -1) {
                sendResponse(null, 400, aggregationServer.clock.increment());
                return;
            }

            // Update lamport clock
            aggregationServer.clock.updateOnReceive(receivedLamportClock);

            // Below parts are for queueing requests: serve request that has lower lamport clock
            // Create and queue the request
            Request request = new Request(
                    "GET",
                    stationID,
                    receivedLamportClock,
                    null
            );
            aggregationServer.queueRequest(request);

            // Wait for response with timeout
            try {
                String response = request.getResponse().get(30, TimeUnit.SECONDS);

                if (response.isEmpty() || response == null) {
                    sendResponse(null, 404, aggregationServer.clock.increment());
                } else {
                    sendResponse(response, 200, aggregationServer.clock.increment());
                }
            } catch (TimeoutException e) {
                LOGGER.warning("Request timeout for station: " + stationID + "\n");
                sendResponse(null, 408, aggregationServer.clock.increment());
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warning("Error waiting for response: " + e.getMessage() + "\n");
                sendResponse(null, 500, aggregationServer.clock.increment());
            }

        } catch (Exception e) {
            LOGGER.warning("Error processing GET request: " + e.getMessage() + "\n");
            sendResponse(null, 500, aggregationServer.clock.increment());
        }
    }

    private void handlePutRequest() throws IOException {
        // Status updates and initialization
        LOGGER.info("Processing PUT request\n");
        String line;
        int receivedLamportClock = -1;
        int contentLength = -1;

        try {
            // 1. Parse headers
            while (((line = in.readLine()) != null) && (!line.isEmpty())) {
                if (line.startsWith("Lamport-Clock:")) {
                    try {
                        receivedLamportClock = Integer.parseInt(line.split(":")[1].trim());
                        LOGGER.fine("Received Lamport clock: " + receivedLamportClock + "\n");
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid Lamport clock format: " + e.getMessage() + "\n");
                        sendResponse(null, 400, aggregationServer.clock.increment());
                        return;
                    }
                } else if (line.startsWith("Content-Length:")) {
                    try {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                        LOGGER.fine("Content length: " + contentLength + "\n");
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid content length format: " + e.getMessage() + "\n");
                        sendResponse(null, 400, aggregationServer.clock.increment());
                        return;
                    }
                }
            }

            // 2. Validate headers: send error response if relevant properties are wrong
            if (receivedLamportClock == -1 || contentLength == -1) {
                LOGGER.warning("Missing required headers\n");
                sendResponse(null, 400, aggregationServer.clock.increment());
                return;
            }

            // 3. Read JSON body with proper length handling
            char[] buffer = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = in.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) {
                    LOGGER.warning("Unexpected end of input stream while reading JSON body.\n");
                    sendResponse(null, 400, aggregationServer.clock.increment());
                    return;
                }
                totalRead += read;
            }
            String jsonData = new String(buffer);

            // 4. Validate and parse JSON
            if (jsonData.isEmpty()) {
                LOGGER.warning("Received empty JSON body\n");
                sendResponse(null, 400, aggregationServer.clock.increment());
                return;
            }

            // 5. Update lamport clock
            aggregationServer.clock.updateOnReceive(receivedLamportClock);

            // 6. Get station ID
            JsonNode jsonNode = objectMapper.readTree(jsonData);
            String stationId = null;
            if (jsonNode.has("id")) {
                stationId = jsonNode.get("id").asText();
            }
            if (stationId == null || stationId.isEmpty()) {
                LOGGER.warning("Weather data missing ID.\n");
                sendResponse(null, 400, aggregationServer.clock.increment());
                return;
            }

            // 7. Create and queue request
            Request request = new Request(
                    "PUT",
                    stationId,
                    receivedLamportClock,
                    jsonData
            );
            aggregationServer.queueRequest(request);

            // 8. Wait for request to be served / wait for response
            try {
                boolean isNew = aggregationServer.getWeatherData(stationId) == null;
                String response = request.getResponse().get(30, TimeUnit.SECONDS);
                int statusCode = isNew ? 201 : 200;

                // 9. Handle null case & send appropriate response
                if (response.isEmpty()) {
                    sendResponse(null, statusCode, aggregationServer.clock.increment());
                } else {
                    sendResponse(response, statusCode, aggregationServer.clock.increment());
                }

                LOGGER.info("Successfully processed PUT request for station ID: " + stationId + " (Status: " + statusCode + ")\n");
            } catch (TimeoutException e) {
                LOGGER.warning("Request timeout for PUT request\n");
                sendResponse(null, 408, aggregationServer.clock.increment());
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warning("Error waiting for response: " + e.getMessage() + "\n");
                sendResponse(null, 500, aggregationServer.clock.increment());
            }
        } catch (IOException e) {
            LOGGER.severe("IO Error processing PUT request: " + e.getMessage() + "\n");
            sendResponse(null, 500, aggregationServer.clock.increment());
        }
    }


    private void sendResponse(String message, int statusCode, int lamportClock) throws IOException {
        String response = String.format("HTTP/1.1 %d %s\r\n", statusCode, getStatusMessage(statusCode));
        response += String.format("Content-Type: application/json\r\n");
        response += String.format("Lamport-Clock: %d\r\n", lamportClock);
        response += String.format("Content-Length: %d\r\n", (message != null ? message.length() : 0));
        response += "\r\n"; // Empty line to separate headers from body
        if (message != null) {
            response += message;
        }
        out.write(response);
        out.flush();
        LOGGER.info("Sent response with status " + statusCode + " and body length " + (message != null ? message.length() : 0) + "\n");
    }


    // Helper method to get the status message corresponding to a status code
    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 408: return "Timeout";
            case 409: return "Conflict";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }

    private void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            LOGGER.warning("Error closing connection: " + e.getMessage() + "\n");
        }
    }

}

