package org.aggregationServer;

import java.util.concurrent.CompletableFuture;

/**
 * a Request class that holds request information
 */
public class Request {
    private final String type;  // "GET" or "PUT"
    private final String stationId;
    private final int lamportClock;
    private final String content;  // For PUT requests
    private final CompletableFuture<String> response;

    public Request(String type, String stationId, int lamportClock, String content) {
        this.type = type;
        this.stationId = stationId;
        this.lamportClock = lamportClock;
        this.content = content;
        this.response = new CompletableFuture<>();
    }

    public int getLamportClock() { return lamportClock; }
    public String getStationId() { return stationId; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public CompletableFuture<String> getResponse() { return response; }
}