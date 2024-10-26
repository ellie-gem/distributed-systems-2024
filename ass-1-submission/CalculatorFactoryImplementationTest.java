package org.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RMI
 */
public class CalculatorFactoryImplementationTest {
    protected CalculatorFactoryImplementation factory;

    /**
     * Creates / Locate server (CalculatorFactory) before each test
     * @throws RemoteException when exception is raised in the remote method call
     * @throws MalformedURLException when a malformed URL has occurred
     */
    @BeforeEach
    public void setUp() throws RemoteException, MalformedURLException {
        try {
            // Attempt to connect to an existing registry - throws exception if registry is not running
            LocateRegistry.getRegistry(1099).list();
        } catch (RemoteException e) {
            // If registry is not running, create new one
            LocateRegistry.createRegistry(1099);
        }

        // Initialize factory and bind to RMI registry, object has been exported by super()
        factory = new CalculatorFactoryImplementation();
        Naming.rebind("rmi://localhost:1099/calculatorFactory", factory);
    }

    /**
     * Test for correctly creating a Calculator instance
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testCreateCalculator() throws RemoteException {
        Calculator calc = factory.createCalculator();
        assertNotNull(calc);
    }

    /**
     * Test for correctly checking if the calculator's stack is empty or not
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testIsEmpty() throws RemoteException {
        Calculator calc1 = factory.createCalculator();
        assertTrue(calc1.isEmpty());

        calc1.pushValue(100);
        assertFalse(calc1.isEmpty());
    }

    /**
     * Test for pushOperation on all it's valid string operators whether the result is correct or not
     * @throws NoSuchElementException when the stack is empty
     * @throws IllegalArgumentException when an invalid argument is passed to pushOperation()
     * @throws ArithmeticException when there is 0 in the stack of integers for the operations "lcm" and "gcd"
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testCalculatorOperations() throws NoSuchElementException, IllegalArgumentException, ArithmeticException, RemoteException {
        Calculator calc = factory.createCalculator();

        calc.pushValue(10);
        calc.pushValue(20);
        calc.pushOperation("gcd");
        assertEquals(10, calc.pop());

        calc.pushValue(10);
        calc.pushValue(20);
        calc.pushOperation("lcm");
        assertEquals(20, calc.pop());

        calc.pushValue(10);
        calc.pushValue(-20);
        calc.pushOperation("min");
        assertEquals(-20, calc.pop());

        calc.pushValue(10);
        calc.pushValue(-20);
        calc.pushOperation("max");
        assertEquals(10, calc.pop());
    }

    /**
     * Test for multiple clients, if their stacks can correctly store and pop numbers on their stack
     * @throws NoSuchElementException when the stack is empty
     * @throws IllegalArgumentException when the argument passed is invalid
     * @throws ArithmeticException when there are 0 in the list of numbers for functions "lcm" and "gcd"
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testMultipleCalculatorsStack() throws NoSuchElementException, IllegalArgumentException, ArithmeticException, RemoteException {
        Calculator calc1 = factory.createCalculator();
        Calculator calc2 = factory.createCalculator();
        Calculator calc3 = factory.createCalculator();
        Calculator calc4 = factory.createCalculator();

        calc1.pushValue(10);
        calc1.pushValue(30);
        calc2.pushValue(20);
        calc2.pushValue(-40);
        calc3.pushValue(50);
        calc3.pushValue(60);
        calc4.pushValue(70);
        calc4.pushValue(-80);

        assertEquals(30, calc1.pop());
        assertEquals(-40, calc2.pop());
        assertEquals(60, calc3.pop());
        assertEquals(10, calc1.pop());
        assertEquals(20, calc2.pop());
        assertEquals(-80, calc4.pop());
        assertEquals(70, calc4.pop());
    }

    /**
     * Test for multiple clients, if they can correctly perform operations
     * @throws NoSuchElementException when the stack is empty
     * @throws IllegalArgumentException when the argument passed is invalid
     * @throws ArithmeticException when there are 0 in the list of numbers for functions "lcm" and "gcd"
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testMultipleCalculatorsOperations() throws NoSuchElementException, IllegalArgumentException, ArithmeticException, RemoteException {
        Calculator calc1 = factory.createCalculator();
        Calculator calc2 = factory.createCalculator();
        Calculator calc3 = factory.createCalculator();
        Calculator calc4 = factory.createCalculator();

        calc1.pushValue(10);
        calc2.pushValue(20);
        calc1.pushValue(-30);
        calc2.pushValue(40);
        calc3.pushValue(-50);
        calc3.pushValue(60);

        calc1.pushOperation("min");
        assertEquals(-30, calc1.pop());

        calc2.pushOperation("max");
        assertEquals(40, calc2.pop());

        calc3.pushOperation("gcd");
        assertEquals(10, calc3.pop());

        // Test to call when stack is empty
        assertThrows(NoSuchElementException.class, calc1::pop);

        calc4.pushValue(-50);
        calc4.pushValue(-60);
        calc4.pushOperation("lcm");
        assertEquals(300, calc4.pop());
    }

    /**
     * Test the delay in pop for one client
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testDelayPop() throws RemoteException {
        Calculator calc1 = factory.createCalculator();
        calc1.pushValue(30);
        assertEquals(30, calc1.delayPop(1000));
    }

    /**
     * Test the delay in pop for multiple concurrent requests from the same client
     * @throws RemoteException when exception is raised in the remote method call
     * @throws InterruptedException when a thread is interrupted before or during waiting, sleeping or occupied
     */
    @Test
    public void testDelayPopWithConcurrentOperations() throws RemoteException, InterruptedException {
        Calculator calc1 = factory.createCalculator();
        calc1.pushValue(30);

        Thread delayPopThread = new Thread(() -> {
           try {
               assertEquals(20, calc1.delayPop(5000));
               assertEquals(210, calc1.delayPop(10000));
           } catch (RemoteException e) {
               throw new RuntimeException(e);
           }
        });

        Thread pushThread = new Thread(() -> {
            try {
                calc1.pushValue(20);
                Thread.sleep(10000);
                calc1.pushValue(70);
                calc1.pushOperation("lcm");
            } catch (RemoteException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        delayPopThread.start();
        pushThread.start();

        delayPopThread.join();
        pushThread.join();
    }

    /**
     * List of operations for each thread (to be used in the next test as part of assertion checks)
     */
    static List<List<String>> operationsList = Arrays.asList(
            List.of("min"),
            List.of("lcm"),
            List.of("max"),
            List.of("gcd")
    );

    /**
     * List of numbers that goes into the calculator stacks (to be used in the next test as part of assertion checks)
     */
    static int[] numberStack = {12, 7, 93, -45, 61, -29, 84, 56, -73, 34};

    /**
     * List of pre-calculated results (to be used in the next test as part of assertion checks)
     */
    static int[] checkResults = {-73, 1110780, 93, 1};

    /**
     * This test simulates multiple clients with concurrent requests
     * @throws RemoteException when exception is raised in the remote method call
     * @throws InterruptedException when a thread is interrupted before or during waiting, sleeping or occupied
     */
    @Test
    public void testMultipleClientsConcurrentOperations() throws RemoteException, InterruptedException {
        int numClients = operationsList.size();

        // Create thread pool to simulate multiple clients
        ExecutorService executorService = Executors.newFixedThreadPool(numClients);
        CountDownLatch latch = new CountDownLatch(numClients);

        // Each client perform a series of operations on the calculator
        for (int i = 0; i < numClients; i++) {
            // final keyword prevent threads from modifying it within the loop, each thread gets 1 consistent value
            final int clientIndex = i;
            executorService.submit(() -> {
                try {
                    Calculator calc = factory.createCalculator();

                    // Get current client's list of operations to perform
                    List<String> ops = operationsList.get(clientIndex);

                    // Push the stack with predefined numbers
                    for (int j : numberStack) {
                        calc.pushValue(j);
                    }

                    // Perform operations
                    for (String op : ops) {
                        calc.pushOperation(op);
                    }

                    // Store result
                    assertEquals(checkResults[clientIndex], calc.pop());

                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                } finally {
                    System.out.println("Thread " + clientIndex + " complete");
                    latch.countDown();
                }
            });
        }

        // Ensure all threads have finished
        latch.await();

        executorService.shutdown();
    }
}