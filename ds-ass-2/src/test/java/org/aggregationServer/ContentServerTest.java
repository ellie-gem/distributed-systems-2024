package org.aggregationServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class ContentServerTest {
    private static final String TEST_SERVER = "localhost";
    private int TEST_PORT = 4567;
    private ContentServer contentServer;
    private AggregationServer server;
    private static ObjectMapper objectMapper;
    private static String testJsonData;
    private static JsonNode testJsonNode;
    private static WeatherData testWeatherData;

    /**
     //     * Utility method to load sample data from content-server-files/
     //     * @param filename string representing the file name
     //     * @return a JSON formatted test data
     //     * @throws IOException when error occurs during
     //     */
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
        // Start server with logging disabled
        server = new AggregationServer(TEST_PORT, false);
        TEST_PORT = server.getServerSocket().getLocalPort();
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Create content server
        contentServer = new ContentServer(
                TEST_SERVER,
                TEST_PORT,
                "content-server-files/CS01.txt",
                "TestServer"
        );

        Thread.sleep(1000); // Wait for server to start
    }

    @AfterEach
    void tearDown() {
        server.stopServer();
        contentServer.shutdown();
    }

    @Test
    void testPeriodicPutRequests() throws Exception {
        // Reduce interval for testing
        contentServer.set_put_interval_ms(2000); // 2 seconds

        // Start content server
        Thread serverThread = new Thread(contentServer);
        serverThread.start();

        // Wait for first PUT
        Thread.sleep(2500);

        // Verify data was sent
        WeatherData storedData = server.getWeatherData(testWeatherData.getId());
        assertNotNull(storedData, "First PUT should store data");

        // Wait for second PUT
        Thread.sleep(2500);

        // Verify data was updated
        WeatherData updatedData = server.getWeatherData(testWeatherData.getId());
        assertNotNull(updatedData, "Second PUT should update data");

        contentServer.shutdown();
        serverThread.join(5000);
    }

    @Test
    void testRetryMechanism() throws Exception {
        // Stop the real server to simulate failure
        server.stopServer();
        Thread.sleep(1000);

        // Start content server
        Thread serverThread = new Thread(contentServer);
        serverThread.start();

        // Wait for retries (MAX_RETRIES * RETRY_DELAY_MS + 1000)
        Thread.sleep(3 * 1000 + 1000);

        // Server should have attempted MAX_RETRIES times
        // Verify through logs or add a counter to track attempts

        contentServer.shutdown();
        serverThread.join(5000);
    }

    @Test
    void testConcurrentContentServers() throws Exception {
        int numServers = 5;
        CountDownLatch allServersReady = new CountDownLatch(numServers);
        CountDownLatch allServersComplete = new CountDownLatch(numServers);
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numServers);

        try {
            // Create and start multiple content servers
            for (int i = 0; i < numServers; i++) {
                final int index = i;
                Future<?> future = executor.submit(() -> {
                    try {
                        ContentServer server = new ContentServer(
                                TEST_SERVER,
                                TEST_PORT,
                                "content-server-files/CS0" + (index + 1) + ".txt",
                                "TestServer-" + index
                        );

                        // Signal ready
                        allServersReady.countDown();

                        // Wait for all servers
                        allServersReady.await();

                        // Run server
                        Thread serverThread = new Thread(server);
                        serverThread.start();

                        // Wait for one PUT
                        Thread.sleep(2000);

                        // Shutdown
                        server.shutdown();
                        serverThread.join(5000);

                        // Signal complete
                        allServersComplete.countDown();

                    } catch (Exception e) {
                        fail("Server " + index + " failed: " + e.getMessage());
                    }
                });
                futures.add(future);
            }

            // Wait for completion
            boolean completed = allServersComplete.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "All servers should complete");

            // Verify data from all servers was stored
            for (int i = 0; i < numServers; i++) {
                WeatherData data = server.getWeatherData("IDS6090" + (i + 1));
                assertNotNull(data, "Data from server " + i + " should be stored");
            }

        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testFileParsingError() {
        // Test with non-existent file
        assertThrows(IOException.class, () -> {
            new ContentServer(
                    TEST_SERVER,
                    TEST_PORT,
                    "non-existent-file.txt",
                    "TestServer"
            ).parseFileToJson();
        });
    }

    @Test
    void testLamportClockIncrement() throws Exception {
        // Start server
        Thread serverThread = new Thread(contentServer);
        serverThread.start();

        // Get initial clock value
        int initialClock = contentServer.getClock().getValue();

        // Wait for PUT
        Thread.sleep(2000);

        // Clock should have incremented
        assertTrue(contentServer.getClock().getValue() > initialClock,
                "Lamport clock should increment after PUT");

        contentServer.shutdown();
        serverThread.join(5000);
    }


    @Test
    void testServerConnectionFailures() throws Exception {
        // Start content server when aggregation server is down
        server.stopServer();
        Thread.sleep(1000);  // Wait for server to fully stop

        Thread contentServerThread = new Thread(contentServer);
        contentServerThread.start();

        // Wait for some retry attempts
        Thread.sleep(2000);

        // Start server back up
        server = new AggregationServer(TEST_PORT, false);
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();
        Thread.sleep(2000);

        // Verify content server recovers and sends data
        WeatherData storedData = server.getWeatherData(testWeatherData.getId());
        assertNotNull(storedData, "Should successfully send data after server recovers");

        contentServer.shutdown();
        contentServerThread.join(5000);
    }

}