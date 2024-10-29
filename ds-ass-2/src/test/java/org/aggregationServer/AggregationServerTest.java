package org.aggregationServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AggregationServerTest {
//    private static final int TEST_PORT = 4567;
    private AggregationServer server;
    private ObjectMapper objectMapper;
    private static String testJsonData;        // String format for PUT requests
    private static JsonNode testJsonNode;      // JsonNode for easier field access/manipulation
    private static WeatherData testWeatherData; // POJO for direct comparisons

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

    /**
     * Utility method to help assert equals data for testing
     * @param expected what the test data should look like
     * @param actual data of what the test actually produce
     */
    private void assertWeatherDataEquals(WeatherData expected, WeatherData actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getState(), actual.getState());
        assertEquals(expected.getTimeZone(), actual.getTimeZone());
        assertEquals(expected.getLat(), actual.getLat());
        assertEquals(expected.getLon(), actual.getLon());
        assertEquals(expected.getLocalDateTime(), actual.getLocalDateTime());
        assertEquals(expected.getLocalDateTimeFull(), actual.getLocalDateTimeFull());
        assertEquals(expected.getAirTemp(), actual.getAirTemp());
        assertEquals(expected.getApparentTemp(), actual.getApparentTemp());
        assertEquals(expected.getCloud(), actual.getCloud());
        assertEquals(expected.getDewpt(), actual.getDewpt());
        assertEquals(expected.getPress(), actual.getPress());
        assertEquals(expected.getRelHum(), actual.getRelHum());
        assertEquals(expected.getWindDir(), actual.getWindDir());
        assertEquals(expected.getWindSpdKmh(), actual.getWindSpdKmh());
        assertEquals(expected.getWindSpdKt(), actual.getWindSpdKt());
    }

    /**
     * Method to create a modified version of test data using a new ID
     * @param field test data field
     * @param value test data value
     * @return a copy of the modified version of test data
     */
    private ObjectNode createModifiedTestData(String field, String value) {
        ObjectNode modifiedJson = testJsonNode.deepCopy();
        modifiedJson.put(field, value);
        return modifiedJson;
    }


    /**
     * Utility method to clear runtime files / files generated during running tests
     * @param directoryPath string representing the directory's path
     */
    private static void clearDirectory(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            Files.walk(dir)
                    .filter(Files::isRegularFile) // only touches regular files, not directories
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file); // delete each file
                        } catch (IOException e) {
                            fail("Unable to delete file during teardown" + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            fail("Unable to delete directory" + e.getMessage());
        }
    }


    @BeforeAll
    static void setUpTestData() {
        // Clear all backup-files and runtime-files
        clearDirectory("backup-files");
        clearDirectory("runtime-files");

        try {
            // Load the data once
            Map<String, Object> weatherDataMap = loadTestDataFromFile("content-server-files/CS01.txt");

            ObjectMapper mapper = new ObjectMapper();
            testWeatherData = mapper.convertValue(weatherDataMap, WeatherData.class);
            testJsonData = mapper.writeValueAsString(weatherDataMap);
            testJsonNode = mapper.readTree(testJsonData);

        } catch (IOException e) {
            fail("Failed to load test data: " + e.getMessage());
        }
    }


    @BeforeEach
    void setUp() throws Exception {
        server = new AggregationServer(0, false);
        objectMapper = new ObjectMapper();
        // Start server in separate thread
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();
        // Wait briefly for server to start
        Thread.sleep(5000);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        server.stopServer();

        // Clear all backup-files and runtime-files
        clearDirectory("backup-files");
        clearDirectory("runtime-files");

        Thread.sleep(5000);
    }


    @Test
    void testWeatherDataStorage() {
        // Get test data
        WeatherData testData = this.testWeatherData;

        // Update weather data
        server.updateWeatherData(testData.getId(), testData);

        // Verify data was stored
        WeatherData retrieved = server.getWeatherData(testData.getId());

        // Assert all fields were stored correctly
        assertEquals(testData, retrieved);
    }


    @Test
    void testRequestProcessing() throws Exception {
        // Create test request using stored test data
        Request putRequest = new Request(
                "PUT",
                testJsonNode.get("id").asText(),
                1,
                testJsonData
        );

        server.queueRequest(putRequest);
        putRequest.getResponse().get(5, TimeUnit.SECONDS);

        // Verify data was stored
        WeatherData stored = server.getWeatherData(testJsonNode.get("id").asText());
        assertNotNull(stored);
        assertWeatherDataEquals(testWeatherData, stored);
    }


    @Test
    void testDataExpiration() throws Exception {
        // Add test data
        Request putRequest = new Request(
                "PUT",
                testWeatherData.getId(),
                1,
                testJsonData
        );
        server.queueRequest(putRequest);

        // Wait for PUT to complete
        putRequest.getResponse().get(5, TimeUnit.SECONDS);

        // Verify data was stored initially
        WeatherData initial = server.getWeatherData(testWeatherData.getId());
        assertNotNull(initial, "Data should be stored initially");

        // Wait 60+ seconds (have 10 seconds worth of explicit waiting in between starting server until this line of code)
        // unfortunately my expiration checker runs every 30 seconds after it first starts, so this means,
        // by the 1st expiration checker -> this newly added data won't have been in the map for >= 30 seconds,
        // this means it wil only be removed after the 2nd expiration check -> hence we wait 60+ seconds
        Thread.sleep(61000);

        // Verify data was removed
        assertNull(server.getWeatherData(testWeatherData.getId()),"Data should be removed after expiration");
    }



    @Test
    void testLamportClockOrdering() throws Exception {
        // Create modified versions of test data with different station names
        ObjectNode json1 = testJsonNode.deepCopy();
        json1.put("name", "I'm second!");

        ObjectNode json2 = testJsonNode.deepCopy();
        json2.put("name", "I'm first!");

        // Create requests with different Lamport clocks
        Request request1 = new Request(
                "PUT",
                testWeatherData.getId(),
                2,
                json1.toString()
        );
        Request request2 = new Request(
                "PUT",
                testWeatherData.getId(),
                1,
                json2.toString()
        );

        server.queueRequest(request1);
        server.queueRequest(request2);

        request1.getResponse().get(5, TimeUnit.SECONDS);
        request2.getResponse().get(5, TimeUnit.SECONDS);

        WeatherData stored = server.getWeatherData(testWeatherData.getId());
        assertEquals("I'm second!", stored.getName());  // Should keep higher clock value
    }

    @Test
    void testPersistence() throws Exception {
        // Add test data
        server.updateWeatherData(testWeatherData.getId(), testWeatherData);

        // Stop server
        server.stopServer();

        // Create new server instance
        AggregationServer newServer = new AggregationServer(0, false);

        // Verify data was loaded
        WeatherData loaded = newServer.getWeatherData(testWeatherData.getId());
        assertNotNull(loaded);
        assertWeatherDataEquals(testWeatherData, loaded);
    }


    /**
     * Uses an ExecutorService to run requests in parallel
     * Uses CountDownLatch to synchronize request sending
     * Actually tests concurrent access to the server
     * Has proper thread management and cleanup
     * Includes timeout handling
     * @throws Exception when requests fails to be provided a response
     */
    @Test
    void testConcurrentRequests() throws Exception {
        int numRequests = 10;
        CountDownLatch allRequestsSent = new CountDownLatch(numRequests);
        CountDownLatch allRequestsComplete = new CountDownLatch(numRequests);
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numRequests);

        try {
            // Create and submit concurrent requests
            for (int i = 0; i < numRequests; i++) {
                final int index = i;
                Future<?> future = executor.submit(() -> {
                    try {
                        // Prepare request
                        ObjectNode modifiedJson = testJsonNode.deepCopy();
                        String modifiedId = "IDS6090" + index;
                        modifiedJson.put("id", modifiedId);

                        Request request = new Request(
                                "PUT",
                                modifiedId,
                                index,
                                modifiedJson.toString()
                        );

                        // Signal request is ready
                        allRequestsSent.countDown();

                        // Wait for all requests to be ready
                        allRequestsSent.await();

                        // Send request
                        server.queueRequest(request);

                        // Wait for response
                        request.getResponse().get(5, TimeUnit.SECONDS);

                        // Signal completion
                        allRequestsComplete.countDown();

                    } catch (Exception e) {
                        fail("Request " + index + " failed: " + e.getMessage());
                    }
                });
                futures.add(future);
            }

            // Wait for all requests to complete with timeout
            boolean completed = allRequestsComplete.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "Not all requests completed in time");

            // Verify all data was stored correctly
            for (int i = 0; i < numRequests; i++) {
                WeatherData data = server.getWeatherData("IDS6090" + i);
                assertNotNull(data, "Data missing for request " + i);
                assertEquals("IDS6090" + i, data.getId());
            }

        } finally {
            // Clean up executor
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Tests for concurrent GET and PUT + different ordering. This test verifies that:
     * When PUT arrives first:
     *      GET gets the data
     *      Both operations succeed
     * When GET arrives first:
     *      GET returns empty
     *      Subsequent PUT still succeeds
     *      Final state is correct
     * @throws Exception when request fails
     */
    @Test
    void testConcurrentGetAndPutOrdering() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // First scenario: PUT (1) then GET (2)
            Future<?> putFuture = executor.submit(() -> {
                try {
                    startLatch.await();
                    Request putRequest = new Request("PUT", "IDS60901", 1, testJsonData);
                    server.queueRequest(putRequest);
                    putRequest.getResponse().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail("PUT request failed: " + e.getMessage());
                }
            });

            Future<?> getFuture = executor.submit(() -> {
                try {
                    startLatch.await();
                    Request getRequest = new Request("GET", "IDS60901", 2, null);
                    server.queueRequest(getRequest);
                    String response = getRequest.getResponse().get(5, TimeUnit.SECONDS);
                    assertFalse(response.isEmpty());  // Should get data
                } catch (Exception e) {
                    fail("GET request failed: " + e.getMessage());
                }
            });

            startLatch.countDown();
            putFuture.get(10, TimeUnit.SECONDS);
            getFuture.get(10, TimeUnit.SECONDS);

            // Clear data for next test
            server.clearTestData();

            // Second scenario: GET (2) then PUT (1)
            CountDownLatch startLatch2 = new CountDownLatch(1);

            Future<?> getFuture2 = executor.submit(() -> {
                try {
                    startLatch2.await();
                    Request getRequest = new Request("GET", "IDS60901", 2, null);
                    server.queueRequest(getRequest);
                    String response = getRequest.getResponse().get(5, TimeUnit.SECONDS);
                    assertTrue(response.isEmpty());  // Should be empty - data doesn't exist yet
                } catch (Exception e) {
                    fail("GET request failed: " + e.getMessage());
                }
            });

            Future<?> putFuture2 = executor.submit(() -> {
                try {
                    startLatch2.await();
                    Thread.sleep(100);  // Small delay to ensure GET processes first
                    Request putRequest = new Request("PUT", "IDS60901", 1, testJsonData);
                    server.queueRequest(putRequest);
                    putRequest.getResponse().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail("PUT request failed: " + e.getMessage());
                }
            });

            startLatch2.countDown();
            getFuture2.get(10, TimeUnit.SECONDS);
            putFuture2.get(10, TimeUnit.SECONDS);

            // Verify final state
            WeatherData finalData = server.getWeatherData("IDS60901");
            assertNotNull(finalData);  // Data should exist
            assertWeatherDataEquals(testWeatherData, finalData);

        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }


    @Test
    void testTimeoutHandling() {
        // Create a delayed CompletableFuture (mock)
        CompletableFuture<String> delayedResponse = new CompletableFuture<>();

        // Set a delay on the CompletableFuture to simulate a long-running response
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(32000); // Simulate 32 seconds delay
                delayedResponse.complete("Delayed response");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Create a Request with the delayed future instead of the real getResponse()
        Request request = new Request(
                "GET",
                testWeatherData.getId(),
                1,
                null
        ) {
            // verride the getResponse() method in the Request instance to return delayedResponse (defined to delay for 32 seconds)
            @Override
            public CompletableFuture<String> getResponse() {
                return delayedResponse;
            }
        };

        // Get response in 30 seconds (as defined from code -> shouldnt complete since we set it to delay for 32 seconds)
        assertThrows(TimeoutException.class, () -> {
            request.getResponse().get(30, TimeUnit.SECONDS);
        });
    }
}