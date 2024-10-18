package org.aggregationServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
     * @param clientSocket a socket (passed to client handler from accepting the connection request)
     *                     that is used to communicate with the client
     * @throws
     */
    public RequestHandler(Socket clientSocket, AggregationServer aggServer) throws IOException {
        this.clientSocket = clientSocket;
        this.aggregationServer = aggServer;
        this.objectMapper = new ObjectMapper();

        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        } catch (IOException e) {
            LOGGER.severe("Error creating I/O streams: " + e.getMessage());
            closeConnection();
        }
    }

    /**
     * Starts the handler when a connection is made between client and server
     */
    @Override
    public void run() {
        try {
            String requestLine = in.readLine();

            if (requestLine != null && !requestLine.isEmpty()) {
                if (requestLine.startsWith("GET")) {
                    handleGetRequest(requestLine);
                } else if (requestLine.startsWith("PUT")) {
                    handlePutRequest(requestLine);
                } else {
                    sendResponse(null, 400, aggregationServer.getLamportClock());
                }
            } else {
                // In the case where request is incomplete or the client closed the connection
                sendResponse(null, 400, aggregationServer.getLamportClock());
            }

        } catch (IOException e) {
            LOGGER.warning("Error handling request: " + e.getMessage());
            try {
                sendResponse(null, 500, aggregationServer.getLamportClock());
            } catch (IOException ioException) {
                LOGGER.severe("Failed to send error response: " + ioException.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    /**
     * Parses the GET request and then queries the hashmap
     * @throws IOException
     */
    private void handleGetRequest(String requestLine) throws IOException {
        try {
            // Parse
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                sendResponse(null, 400, aggregationServer.getLamportClock());
                return;
            }

            String url = requestParts[1];
            String[] urlParts = url.split("\\?");
            if (urlParts.length < 2 || !urlParts[0].equals("/weather_data.json")) {
                sendResponse(null, 400, aggregationServer.getLamportClock());
                return;
            }

            // Parse query parameters (stationID=<id>)
            String queryParams = urlParts[1];
            String[] queryParamsArray = queryParams.split("&");
            String stationID = null;
            int clientLamportClock = -1;

            for (String param : queryParamsArray) {
                if (param.startsWith("station-id=")) {
                    stationID = param.split("=")[1];
                } else if (param.startsWith("lamport-clock=")) {
                    try {
                        clientLamportClock = Integer.parseInt(param.split("=")[1]);
                    } catch (NumberFormatException e) {
                        sendResponse(null, 400, aggregationServer.getLamportClock());
                        return;
                    }
                }
            }

            if (stationID == null || clientLamportClock == -1) {
                sendResponse(null, 400, aggregationServer.getLamportClock());
                return;
            }

            // Update lamport clock
            int newLamportClock = aggregationServer.updateLamportClock(clientLamportClock);

            WeatherData data = aggregationServer.getWeatherData(stationID);
            if (data == null) {
                sendResponse(null, 404, newLamportClock);
            } else {
                String jsonResponse = objectMapper.writeValueAsString(data);
                sendResponse(jsonResponse, 200, newLamportClock);
            }

        } catch (Exception e) {
            LOGGER.warning("Error processing GET request: " + e.getMessage());
            sendResponse(null, 500, aggregationServer.getLamportClock());
        }
    }

    private void handlePutRequest(String request) throws IOException {
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        int clientLamportClock = -1;

        try {
            while (!(line = in.readLine()).isEmpty()) {
                if (line.startsWith("Lamport-Clock:")) {
                    try {
                        clientLamportClock = Integer.parseInt(line.split(":")[1].trim());
                    } catch (NumberFormatException e) {
                        sendResponse(null, 400, aggregationServer.getLamportClock());
                        return;
                    }
                }
            }

            while (in.ready()) {
                jsonBuilder.append(in.readLine());
            }

            String jsonData = jsonBuilder.toString();
            if (jsonData.isEmpty() || clientLamportClock == -1) {
                sendResponse(null, 400, aggregationServer.getLamportClock());
                return;
            }

            int newLamportClock = aggregationServer.updateLamportClock(clientLamportClock);

            WeatherData weatherData = objectMapper.readValue(jsonData, WeatherData.class);
            boolean isNew = aggregationServer.getWeatherData(weatherData.getId()) == null;

            aggregationServer.updateWeatherData(weatherData.getId(), weatherData);
            sendResponse(null, isNew ? 201 : 202, newLamportClock);

        } catch (Exception e) {
            LOGGER.warning("Error processing PUT request: " + e.getMessage());
            sendResponse(null, 500, aggregationServer.getLamportClock());
        }
    }

    /**
     *
     * @param message http body
     * @throws IOException
     */
    // Helper method to send an HTTP response
    private void sendResponse(String message, int statusCode, int lamportClock) throws IOException {
        out.write("HTTP/1.1 " + statusCode + " " + getStatusMessage(statusCode));
        out.newLine();
        out.write("Content-Type: application/json");
        out.newLine();
        out.write("Lamport-Clock: " + lamportClock);
        out.newLine();
        out.write("Content-Length: " + (message != null ? message.length() : 0));
        out.newLine();
        out.newLine();

        if (message != null) {
            out.write(message);
        }
        out.flush();
    }

    // Helper method to get the status message corresponding to a status code
    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 404: return "Not Found";
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
            LOGGER.warning("Error closing connection: " + e.getMessage());
        }
    }

}

// UPDATE CODE FOR REMOVING STALE DATA
