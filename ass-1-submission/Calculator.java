package org.calculator;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.NoSuchElementException;

/**
 * The remote interface that defines the remote operations implemented by the remote service
 */
public interface Calculator extends Remote {
    /**
     * Pushes a value to the top of the stack
     * @param val an integer
     * @throws RemoteException when exception is raised in the remote method call
     */
    void pushValue(int val) throws RemoteException;

    /**
     * Takes a valid string operator, performs the operation by taking in all numbers on the stack,
     * clears the stack, then pushes the result to the stack.
     * @param operator a string that is of {"lcm", "gcd", "min", "max"}
     * @throws NoSuchElementException when the operation tries to perform on an empty stack
     * @throws IllegalArgumentException when the string operator is not of {"lcm", "gcd", "min", "max"}
     * @throws ArithmeticException when there is 0 in the stack for operations "lcm" and "gcd"
     * @throws RemoteException when exception is raised in the remote method call
     */
    void pushOperation(String operator) throws NoSuchElementException, IllegalArgumentException, ArithmeticException, RemoteException;

    /**
     * Pops top of stack and returns it to client
     * @return the integer at the top of the stack
     * @throws NoSuchElementException when stack is empty
     * @throws RemoteException when exception is raised in the remote method call
     */
    int pop() throws NoSuchElementException, RemoteException;

    /**
     * Checks if stack is empty
     * @return true if stack is empty, false otherwise
     */
    boolean isEmpty() throws RemoteException;

    /**
     * Waits 'millis' milliseconds before carrying out the pop operation
     * @param millis an integer
     * @return the integer at the top of the stack
     * @throws NoSuchElementException when stack is empty
     * @throws RemoteException when exception is raised in the remote method call
     */
    int delayPop(int millis) throws NoSuchElementException, RemoteException;
}
