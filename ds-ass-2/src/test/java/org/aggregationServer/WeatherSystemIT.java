package org.aggregationServer;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WeatherSystemIT {

    private static AggregationServer aggregationServer;
    private static final int PORT = 8000;
    private static final String HOST = "localhost";

    @BeforeAll
    static void setup() throws IOException {
        // Start the Aggregation Server
        aggregationServer = new AggregationServer(PORT);
        new Thread(() -> {
            aggregationServer.start();
        }).start();

        // Give the server some time to start
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    static void tearDown() {
        // Stop the Aggregation Server
        aggregationServer.stopServer();
    }

    @Test
    void testServerContentPutAndClientGet() throws IOException {
        // Create a weather data object
        WeatherData inputData = new WeatherData();
        inputData.setId("IDS60901");
        inputData.setName("Adelaide (West Terrace / ngayirdapira)");
        inputData.setState("SA");
        inputData.setTimeZone("CST");
        inputData.setLat(-34.9);
        inputData.setLon(138.6);
        inputData.setLocalDateTime("15/04:00pm");
        inputData.setLocalDateTimeFull("20230715160000");
        inputData.setAirTemp(13.3);
        inputData.setApparentTemp(9.5);
        inputData.setCloud("Partly cloudy");
        inputData.setDewpt(5.7);
        inputData.setPress(1023.9);
        inputData.setRelHum(60);
        inputData.setWindDir("S");
        inputData.setWindSpdKmh(15);
        inputData.setWindSpdKt(8);

        // Create and use ContentServer to put data
        ContentServer contentServer = new ContentServer(HOST, PORT);
        boolean putResult = contentServer.sendPutRequest(inputData);
        assertTrue(putResult, "PUT request should be successful");

        // Give some time for the server to process the data
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create and use GETClient to retrieve data
        GETClient getClient = new GETClient(HOST, PORT);
        WeatherData retrievedData = getClient.sendGetRequest("IDS60901");

        // Assert that the retrieved data matches the input data
        assertNotNull(retrievedData, "Retrieved weather data should not be null");
        assertEquals(inputData.getId(), retrievedData.getId(), "Station ID should match");
        assertEquals(inputData.getName(), retrievedData.getName(), "Station name should match");
        assertEquals(inputData.getState(), retrievedData.getState(), "State should match");
        assertEquals(inputData.getTimeZone(), retrievedData.getTimeZone(), "Time zone should match");
        assertEquals(inputData.getLat(), retrievedData.getLat(), 0.0001, "Latitude should match");
        assertEquals(inputData.getLon(), retrievedData.getLon(), 0.0001, "Longitude should match");
        assertEquals(inputData.getLocalDateTime(), retrievedData.getLocalDateTime(), "Local date time should match");
        assertEquals(inputData.getLocalDateTimeFull(), retrievedData.getLocalDateTimeFull(), "Full local date time should match");
        assertEquals(inputData.getAirTemp(), retrievedData.getAirTemp(), 0.1, "Air temperature should match");
        assertEquals(inputData.getApparentTemp(), retrievedData.getApparentTemp(), 0.1, "Apparent temperature should match");
        assertEquals(inputData.getCloud(), retrievedData.getCloud(), "Cloud cover should match");
        assertEquals(inputData.getDewpt(), retrievedData.getDewpt(), 0.1, "Dew point should match");
        assertEquals(inputData.getPress(), retrievedData.getPress(), 0.1, "Pressure should match");
        assertEquals(inputData.getRelHum(), retrievedData.getRelHum(), "Relative humidity should match");
        assertEquals(inputData.getWindDir(), retrievedData.getWindDir(), "Wind direction should match");
        assertEquals(inputData.getWindSpdKmh(), retrievedData.getWindSpdKmh(), "Wind speed in km/h should match");
        assertEquals(inputData.getWindSpdKt(), retrievedData.getWindSpdKt(), "Wind speed in knots should match");
    }
}