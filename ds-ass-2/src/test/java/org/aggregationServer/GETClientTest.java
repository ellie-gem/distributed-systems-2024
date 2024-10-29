package org.aggregationServer;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GETClientTest {
    private static final String TEST_SERVER = "localhost";
    private int TEST_PORT = 0;
    private GETClient client;
    private AggregationServer server;
    private static ObjectMapper objectMapper;
    private static String testJsonData;
    private static JsonNode testJsonNode;
    private static WeatherData testWeatherData;

    /**
     * Utility method to load sample data from content-server-files/
     * @param filename string representing the file name
     * @return a JSON formatted test data
     * @throws IOException when error occurs during
     */
    private static Map<String, Object> loadTestDataFromFile(String filename) throws IOException {
        Map<String, Object> weatherDataMap = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(filename));

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();

                // Convert numeric values appropriately
                if (key.equals("local_date_time_full")) {  // handle manually (into string instead of int)
                    weatherDataMap.put(key, value);
                } else if (value.matches("-?\\d+\\.\\d+")) {  // Double values
                    weatherDataMap.put(key, Double.parseDouble(value));
                } else if (value.matches("-?\\d+")) {  // Integer values
                    weatherDataMap.put(key, Integer.parseInt(value));
                } else {  // String values
                    weatherDataMap.put(key, value);
                }
            }
        }
        return weatherDataMap;
    }

    @BeforeAll
    static void setUpTestData() {
        try {
            // Load test data
            Map<String, Object> weatherDataMap = loadTestDataFromFile("content-server-files/CS01.txt");
            objectMapper = new ObjectMapper();
            testWeatherData = objectMapper.convertValue(weatherDataMap, WeatherData.class);
            testJsonData = objectMapper.writeValueAsString(weatherDataMap);
            testJsonNode = objectMapper.readTree(testJsonData);
        } catch (IOException e) {
            fail("Failed to load test data: " + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Start server with logging disabled, update the server's port number
        server = new AggregationServer(0, false);
        TEST_PORT = server.getServerSocket().getLocalPort();
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Create client
        client = new GETClient(TEST_SERVER, TEST_PORT, "TestClient");
        Thread.sleep(1000); // Wait for server to start
    }

    @AfterEach
    void tearDown() {
        server.stopServer();
        client.shutdown();
    }

    /**
     * Verifies that the GETClient can correctly:
     * - Connect to server and make a GET request
     * - Receive and process a valid response
     * - Handle the response correctly
     * @throws Exception when get request gets interrupted
     */
    @Test
    void testValidStationIDRequest() throws Exception {
        // First PUT some data into server
        Request putRequest = new Request(
                "PUT",
                testWeatherData.getId(),
                1,
                testJsonData
        );
        server.queueRequest(putRequest);
        putRequest.getResponse().get(5, TimeUnit.SECONDS);

        // Verify data is in server
        WeatherData storedData = server.getWeatherData(testWeatherData.getId());
        assertNotNull(storedData, "Data should be stored in server");

        // Keep track of response received
        AtomicBoolean responseReceived = new AtomicBoolean(false);

        // Start client thread
        Thread clientThread = new Thread(() -> {
            try {
                client.addRequest(testWeatherData.getId());
                Thread.sleep(2000);  // Give time for response
                responseReceived.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        clientThread.start();

        // Wait for client thread to complete
        clientThread.join(5000);

        // Verify
        assertTrue(responseReceived.get(), "Should have received response");

        // Cleanup
        client.shutdown();
    }


    @Test
    void testConcurrentRequests() throws Exception {
        // First put test data into server for clients to request
        for (int i = 0; i < 10; i++) {
            ObjectNode modifiedJson = testJsonNode.deepCopy();
            modifiedJson.put("id", "IDS6090" + i);
            Request putRequest = new Request(
                    "PUT",
                    "IDS6090" + i,
                    i,
                    modifiedJson.toString()
            );
            server.queueRequest(putRequest);
            putRequest.getResponse().get(5, TimeUnit.SECONDS);
        }

        int numClients = 10;
        CountDownLatch allClientsReady = new CountDownLatch(numClients);
        CountDownLatch allClientsComplete = new CountDownLatch(numClients);
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        Map<Integer, String> clientResponses = new ConcurrentHashMap<>();  // Store responses

        try {
            // Create and submit concurrent client requests
            for (int i = 0; i < numClients; i++) {
                final int index = i;
                Future<?> future = executor.submit(() -> {
                    try {
                        // Create client
                        GETClient client = new GETClient(
                                TEST_SERVER,
                                TEST_PORT,
                                "TestClient-" + index
                        );
                        Thread clientThread = new Thread(client);
                        clientThread.start();

                        // Signal client is ready
                        allClientsReady.countDown();

                        // Wait for all clients to be ready
                        allClientsReady.await();

                        // Send request
                        client.addRequest("IDS6090" + index);

                        // Wait briefly for request to be processed
                        Thread.sleep(1000);

                        // Shutdown client
                        client.shutdown();
                        clientThread.join(5000);

                        // Signal completion
                        allClientsComplete.countDown();

                    } catch (Exception e) {
                        fail("Client " + index + " failed: " + e.getMessage());
                    }
                });
                futures.add(future);
            }

            // Wait for all clients to complete with timeout
            boolean completed = allClientsComplete.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "Not all clients completed in time");

            // Verify server still has all data
            for (int i = 0; i < numClients; i++) {
                WeatherData data = server.getWeatherData("IDS6090" + i);
                assertNotNull(data, "Data missing for client " + i);
                assertEquals("IDS6090" + i, data.getId());
            }

        } finally {
            // Clean up executor
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Verifies that GETClient properly handles the case when it requests data
     * for a station ID that doesn't exist in the server.
     * @throws Exception when thread running the get request gets interrupted
     */
    @Test
    void testRequestNonExistentStation() throws Exception {
        // First verify server doesn't have this station
        assertNull(server.getWeatherData("IDS00000"),
                "Server should not have data for non-existent station");

        // Create a GET request directly to verify server response
        Request getRequest = new Request(
                "GET",
                "IDS00000",
                1,
                null
        );
        server.queueRequest(getRequest);

        // Wait for response
        String response = getRequest.getResponse().get(5, TimeUnit.SECONDS);
        System.out.println(response);
        assertTrue(response.isEmpty(), "Response should be empty for non-existent station");

        // Now test through client
        Thread clientThread = new Thread(client);
        clientThread.start();

        client.addRequest("IDS00000");

        // Wait a bit for request to process
        Thread.sleep(2000);

        // Cleanup
        client.shutdown();
        clientThread.join(5000);
    }

    @Test
    void testClientShutdown() throws Exception {
        Thread clientThread = new Thread(client);
        clientThread.start();

        // Add some requests
        client.addRequest(testWeatherData.getId());
        Thread.sleep(1000);

        // Initiate shutdown
        client.shutdown();

        // Wait for thread to finish
        clientThread.join(5000);
        assertFalse(clientThread.isAlive(), "Client thread should terminate after shutdown");
    }

    @Test
    void testLamportClockIncrement() throws Exception {
        int initialClock = client.getClock().getValue();

        // Send request
        Thread clientThread = new Thread(client);
        clientThread.start();

        client.addRequest(testWeatherData.getId());
        Thread.sleep(1000);  // Wait for request to be processed

        assertTrue(client.getClock().getValue() > initialClock,
                "Lamport clock should increment after request");

        client.shutdown();
        clientThread.join(5000);
    }
}