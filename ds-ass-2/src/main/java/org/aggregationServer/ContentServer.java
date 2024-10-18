package org.aggregationServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

public class ContentServer {
    private static final Logger LOGGER = Logger.getLogger(ContentServer.class.getName());
    private final String serverAddress;
    private final int serverPort;
    private final String filePath;
    private AtomicInteger lamportClock;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int PUT_INTERVAL_MS = 5000; // 5 seconds between PUTs
    private final ObjectMapper objectMapper;

    public ContentServer(String serverAddress, int serverPort, String filePath) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.filePath = filePath;
        this.lamportClock = new AtomicInteger(0);
        this.objectMapper = new ObjectMapper();
        setupLogging();
    }

    private void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("contentserver.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
        } catch (IOException e) {
            LOGGER.severe("Failed to set up file logging: " + e.getMessage());
        }
    }

    private Map<String, Object> parseFileToJson() throws IOException {
        Map<String, Object> weatherData = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    weatherData.put(parts[0].trim(), parts[1].trim());
                }
            }
            if (!weatherData.containsKey("id")) {
                throw new IOException("File is missing 'id' field. Rejecting the feed.");
            }
        } catch (NoSuchFileException e) {
            LOGGER.severe("File not found: " + filePath);
            throw new IOException("File not found: " + filePath, e);
        } catch (IOException e) {
            LOGGER.severe("Error reading file: " + e.getMessage());
            throw e;
        }
        return weatherData;
    }

    private boolean sendPutRequest(Map<String, Object> weatherData) throws IOException {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Socket socket = new Socket(serverAddress, serverPort);
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                int currentClock = lamportClock.incrementAndGet();
                String jsonString = objectMapper.writeValueAsString(weatherData);

                // Send PUT request
                out.write("PUT /weather.json HTTP/1.1\r\n");
                out.write("Host: " + serverAddress + "\r\n");
                out.write("User-Agent: ATOMClient/1/0\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("Content-Length: " + jsonString.length() + "\r\n");
                out.write("Lamport-Clock: " + currentClock + "\r\n");
                out.write("\r\n");
                out.write(jsonString);
                out.flush();

                // Read response
                String statusLine = in.readLine();
                if (statusLine == null) {
                    throw new IOException("No response from server");
                }

                String[] statusParts = statusLine.split(" ", 3);
                int statusCode = Integer.parseInt(statusParts[1]);

                LOGGER.info("PUT request sent, Response Code: " + statusCode);

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
                    if (serverLamportClock != -1) {
                        lamportClock.set(Math.max(lamportClock.get(), serverLamportClock) + 1);
                        LOGGER.info("Updated Lamport clock: " + lamportClock.get());
                    }
                    return true;
                } else {
                    LOGGER.warning("Failed to upload weather data. Response Code: " + statusCode);
                    if (attempt < MAX_RETRIES) {
                        LOGGER.info("Retrying in " + RETRY_DELAY_MS + "ms...");
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.severe("Error occurred during PUT request: " + e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new IOException("Failed to send PUT request after " + MAX_RETRIES + " attempts", e);
                }
            }
        }
        return false;
    }

    public void run() {
        try {
            Map<String, Object> weatherData = parseFileToJson();
            while (true) {
                boolean success = sendPutRequest(weatherData);
                if (success) {
                    LOGGER.info("Weather data successfully sent.");
                } else {
                    LOGGER.severe("Failed to send weather data after multiple attempts.");
                }
                Thread.sleep(PUT_INTERVAL_MS);
            }
        } catch (IOException e) {
            LOGGER.severe("Error occurred: " + e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.info("ContentServer interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ContentServer <server_address:port> <file_path>");
            return;
        }

        String[] serverInfo = args[0].split(":");
        if (serverInfo.length != 2) {
            System.out.println("Invalid server address and port format. Use: <server_address:port>");
            return;
        }

        String serverAddress = serverInfo[0];
        int serverPort = Integer.parseInt(serverInfo[1]);
        String filePath = args[1];

        ContentServer contentServer = new ContentServer(serverAddress, serverPort, filePath);
        contentServer.run();
    }
}