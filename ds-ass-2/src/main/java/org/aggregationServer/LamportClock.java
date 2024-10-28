package org.aggregationServer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class LamportClock {
    private AtomicInteger clock;
    private final String entityId;
    private static final Logger LOGGER = Logger.getLogger(LamportClock.class.getName());
    private final String storageFile;
    private final ObjectMapper objectMapper;

    /**
     * Class to store clock state
     */
    private static class ClockState {
        private int clockValue;

        // Default constructor for Jackson
        public ClockState() {}

        public ClockState(int clockValue) {
            this.clockValue = clockValue;
        }

        public int getClockValue() { return clockValue; }
        public void setClockValue(int clockValue) { this.clockValue = clockValue; }
    }

    /**
     * Constructor
     * @param entityId string representing the entity that owns the lamport clock object
     */
    public LamportClock(String entityId) {
        this.entityId = entityId;
        this.objectMapper = new ObjectMapper();
        this.storageFile = "runtime-files/" + entityId.toLowerCase() + "_clock.json";

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get("runtime-files"));
        } catch (IOException e) {
            LOGGER.warning(String.format("[%s] Failed to create directory: %s\n", entityId, e.getMessage()));
        }

        // Initialize clock and try to load saved state
        this.clock = new AtomicInteger(0);
        loadState();
        LOGGER.info(String.format("[%s] Lamport clock initialized at %d\n", entityId, clock.get()));
    }

    /**
     * Load clock state from file
     */
    private void loadState() {
        try {
            File file = new File(storageFile);
            if (file.exists() && file.length() > 0) {
                ClockState state = objectMapper.readValue(file, ClockState.class);
                clock.set(state.getClockValue());
                LOGGER.info(String.format("[%s] Loaded clock value: %d\n", entityId, clock.get()));
            }
        } catch (IOException e) {
            LOGGER.warning(String.format("[%s] Failed to load clock value: %s\n", entityId, e.getMessage()));
        }
    }


    /**
     * Save current clock state to file
     */
    private void saveState() {
        try {
            ClockState state = new ClockState(clock.get());
            objectMapper.writeValue(new File(storageFile), state);
            LOGGER.fine(String.format("[%s] Saved clock value: %d\n", entityId, clock.get()));
        } catch (IOException e) {
            LOGGER.warning(String.format("[%s] Failed to write clock value: %s\n", entityId, e.getMessage()));
        }
    }


    /**
     * @return Current clock value
     */
    public int getValue() {
        return clock.get();
    }


    /**
     * Increment clock value before sending a message
     * @return
     */
    public int increment() {
        int newValue = clock.incrementAndGet();
        saveState(); // Save after increment
        LOGGER.fine(String.format("[%s] Clock incremented to %d\n", entityId, newValue));
        return newValue;
    }


    /**
     * Updates clock when receiving a message
     * Takes max of local clock and received clock, then adds 1
     * @param receivedClock the clock value received from another entity
     * @return the new clock value
     */
    public int updateOnReceive(int receivedClock) {
        int oldValue = clock.get();
        int newValue = clock.updateAndGet(latestValue -> Math.max(latestValue, receivedClock) + 1);
        saveState(); // Save after update
        LOGGER.fine(String.format("[%s] Clock updated from %d to %d (received: %d)\n", entityId, oldValue, newValue, receivedClock));
        return newValue;
    }

    /**
     * Loads a previously saved clock value (for recovery)
     * Only updates if the saved value is greater than current value
     * @param savedClock the saved clock value to restore
     */
    public void restoreFromSaved(int savedClock) {
        clock.updateAndGet(latestValue -> Math.max(latestValue, savedClock));
        saveState();
        LOGGER.info(String.format("[%s] Clock restored to %d\n", entityId, clock.get()));
    }
    
}
