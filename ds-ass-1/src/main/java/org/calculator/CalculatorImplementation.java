package org.calculator;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

/**
 * Implementation class for the remote operations
 */
public class CalculatorImplementation extends UnicastRemoteObject implements Calculator {
    private final Deque<Integer> stack;

    /**
     * Default constructor:
     * Calling super() automatically exports the UnicastRemoteObject so that it can
     * (1) allocate necessary resources for the object to participate in RMI
     * (2) assign the object a port to listen for RMIs
     * (3) returns a stub (proxy that handles communication between client and remote object
     * @throws RemoteException when exception is raised in the remote method call
     */
    protected CalculatorImplementation() throws RemoteException {
        super();
        this.stack = new ArrayDeque<>();
    }

    /**
     * Factory method for creating Calculator instances
     * @return a new Calculator instance
     * @throws RemoteException when exception is raised in the remote method call
     */
    public static Calculator createInstance() throws RemoteException {
        return new CalculatorImplementation();
    }

    /**
     * Utility function for finding the greatest common denominator between 2 numbers
     * @param num1 the first number
     * @param num2 the second number
     * @return the greatest common denominator between the 2 numbers
     * @throws ArithmeticException when there is a 0 in either 2 numbers
     */
    private int gcd(int num1, int num2) throws ArithmeticException {
        if (num1 == 0 || num2 == 0) {
            throw new ArithmeticException("GCD cannot be computed for values of 0.");
        }

        num1 = Math.abs(num1);
        num2 = Math.abs(num2);

        while (num2 != 0) {
            int temp = num2;
            num2 = num1 % num2;
            num1 = temp;
        }
        return num1;
    }

    /**
     * Wrapper function that calls gcd() to find the greatest common denominator from a list of numbers
     * @param nums a stack of integers
     * @return the greatest common denominator from a list of numbers
     * @throws ArithmeticException when there are 0 in the list of numbers
     */
    private int gcd(Deque<Integer> nums) throws ArithmeticException {
        // Check for zeros in the stack
        if (nums.stream().anyMatch(num -> num == 0)) {
            throw new ArithmeticException("Cannot compute GCD when stack contains zeroes.");
        }

        Integer num1 = nums.pop();

        // Edge case if there is only one number
        if (nums.isEmpty()) {
            return num1;
        } else if (nums.size() == 1) {
            Integer num2 = nums.pop();
            return gcd(num1, num2);
        }

        return nums.stream().reduce(num1, this::gcd);
    }

    /**
     * Finds the lowest common multiple between 2 numbers
     * @param num1 The first number
     * @param num2 The second number
     * @return the lowest common multiple between 2 numbers
     * @throws ArithmeticException when one of the numbers is 0
     */
    private int lcm(int num1, int num2) throws ArithmeticException {
        return Math.abs(num1 * num2) / gcd(num1, num2);
    }

    /**
     * Wrapper Function that calls lcm() to find the lowest common multiple from a list of numbers
     * @param nums The list of numbers to check
     * @return the lowest common multiple
     */
    private int lcm(Deque<Integer> nums) {
        // Check for zeros in the stack
        if (nums.stream().anyMatch(num -> num == 0)) {
            throw new ArithmeticException("Cannot compute LCM when stack contains zeroes.");
        }

        return nums.stream().reduce(1, this::lcm);
    }

    /**
     * Pushes a value to the top of the stack
     * @param val an integer
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Override
    public synchronized void pushValue(int val) throws RemoteException {
        stack.push(val);
    }

    /**
     * Takes a valid string operator, performs the operation by taking in all numbers on the stack,
     * clears the stack, then pushes the result to the stack.
     * @param operator a string that is of {"lcm", "gcd", "min", "max"}
     * @throws NoSuchElementException when the operation tries to perform on an empty stack
     * @throws IllegalArgumentException when the string operator is not of {"lcm", "gcd", "min", "max"}
     * @throws ArithmeticException when there is 0 in the stack for operations "lcm" and "gcd"
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Override
    public synchronized void pushOperation(String operator) throws NoSuchElementException, IllegalArgumentException, ArithmeticException, RemoteException {
        if (stack.isEmpty()) {
            throw new NoSuchElementException("Stack is empty, cannot pop elements to perform operation.");
        }

        int result = switch (operator) {
            case "min" -> stack.stream().min(Integer::compareTo).get();
            case "max" -> stack.stream().max(Integer::compareTo).get();
            case "lcm" -> lcm(stack);
            case "gcd" -> gcd(stack);
            default -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };

        stack.clear();
        stack.push(result);
    }

    /**
     * Pops top of stack and returns it to client
     * @return the integer at the top of the stack
     * @throws NoSuchElementException when stack is empty
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Override
    public synchronized int pop() throws NoSuchElementException, RemoteException {
        if (stack.isEmpty()) {
            throw new NoSuchElementException("Stack is empty, cannot perform pop.");
        }
        return stack.pop();
    }

    /**
     * Checks if stack is empty
     * @return true if stack is empty, false otherwise
     */
    @Override
    public synchronized boolean isEmpty() throws RemoteException {
        return stack.isEmpty();
    }

    /**
     * Waits 'millis' milliseconds before carrying out the pop operation
     * @param millis an integer
     * @return the integer at the top of the stack
     * @throws NoSuchElementException when stack is empty
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Override
    public int delayPop(int millis) throws NoSuchElementException, IllegalArgumentException, RemoteException {
        if (millis < 0) {
            throw new IllegalArgumentException("Negative time not allowed");
        }

        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Restore the interrupted status after catching InterruptedException
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }

        synchronized (this) {
            return pop();
        }
    }

}
