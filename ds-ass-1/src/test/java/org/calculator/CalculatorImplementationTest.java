package org.calculator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.rmi.RemoteException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests for CalculatorImplementation
 */
public class CalculatorImplementationTest {
    protected CalculatorImplementation calculator;

    /**
     * Creates a new calculator instance by directly calling the CalculatorImplementation() method
     * @throws RemoteException when exception is raised in the remote method call
     */
    @BeforeEach
    public void setUp() throws RemoteException {
        calculator = new CalculatorImplementation();
    }

    /**
     * Test for correctly checking if the calculator's stack is empty or not
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testIsEmpty() throws RemoteException {
        assertTrue(calculator.isEmpty());
        calculator.pushValue(10);
        assertFalse(calculator.isEmpty());
    }

    /**
     * Test for popping from an empty stack
     * @throws NoSuchElementException when trying to pop from an empty stack
     */
    @Test
    public void testPopEmptyStack() throws NoSuchElementException {
        assertThrows(NoSuchElementException.class, () -> calculator.pop());
    }

    /**
     * Test for correctly popping integers form the top of the stack
     * @throws NoSuchElementException when trying to pop from an empty stack
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPop() throws NoSuchElementException, RemoteException {
        calculator.pushValue(10);
        assertEquals(10, calculator.pop());
    }

    /**
     * Test for correctly pushing integers into the stack
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushValue() throws RemoteException {
        calculator.pushValue(5);
        assertFalse(calculator.isEmpty());
    }

    /**
     * Test for pushing an operation into an empty stack
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationOnEmptyStack() throws NoSuchElementException, RemoteException  {
        assertThrows(NoSuchElementException.class, () -> {
            calculator.pushOperation("min");
        });
    }

    /**
     * Test for pushing an operation into a stack with an invalid argument
     * @throws IllegalArgumentException when an invalid argument is passed
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationWithInvalidOperator() throws IllegalArgumentException, RemoteException  {
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.pushValue(-1);
            calculator.pushOperation("abc");
        });
    }

    /**
     * Test for pushing the operation "min"
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationMin() throws NoSuchElementException, RemoteException {
        calculator.pushValue(10);
        calculator.pushValue(-8756);
        calculator.pushOperation("min");
        assertEquals(-8756, calculator.pop());
    }

    /**
     * Test for pushing the operation "max"
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationMax() throws NoSuchElementException, RemoteException {
        calculator.pushValue(0);
        calculator.pushValue(-8756);
        calculator.pushOperation("max");
        assertEquals(0, calculator.pop());
    }

    /**
     * Test for pushing the operation "gcd" for a stack with ALL zeroes
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationGCDWithALlZeroes() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(0);
        calculator.pushValue(0);
        calculator.pushValue(0);
        assertThrows(ArithmeticException.class, () -> {
            calculator.pushOperation("gcd");
        });
    }

    /**
     * Test for pushing the operation "gcd" for a stack with SOME zeroes
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationGCDWithSomeZeroes() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(0);
        calculator.pushValue(0);
        calculator.pushValue(64);
        calculator.pushValue(180);
        calculator.pushValue(14);
        assertThrows(ArithmeticException.class, () -> {
            calculator.pushOperation("gcd");
        });
    }

    /**
     * Test for pushing the operation "gcd" for a stack with some negatives
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationGCDWithNegatives() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(-1789);
        calculator.pushValue(180);
        calculator.pushValue(-13);
        calculator.pushOperation("gcd");
        assertEquals(1, calculator.pop());
    }

    /**
     * Test for pushing the operation "gcd" normally
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationGCD() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(64);
        calculator.pushValue(180);
        calculator.pushValue(14);
        calculator.pushOperation("gcd");
        assertEquals(2, calculator.pop());
    }

    /**
     * Test for pushing the operation "lcm" for a stack with all zeroes
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationLCMWithALlZeroes() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(0);
        calculator.pushValue(0);
        calculator.pushValue(0);
        assertThrows(ArithmeticException.class, () -> {
            calculator.pushOperation("lcm");
        });
    }

    /**
     * Test for pushing the operation "lcm" for a stack with some zeroes
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationLCMWithSomeZeroes() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(0);
        calculator.pushValue(0);
        calculator.pushValue(10);
        calculator.pushValue(20);
        assertThrows(ArithmeticException.class, () -> {
            calculator.pushOperation("lcm");
        });
    }

    /**
     * Test for pushing the operation "lcm" for a stack with some negatives
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationLCMWithNegatives() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(1);
        calculator.pushValue(-10);
        calculator.pushValue(10);
        calculator.pushValue(-20);
        calculator.pushOperation("lcm");
        assertEquals(20, calculator.pop());
    }

    /**
     * Test for pushing the operation "lcm" normally
     * @throws NoSuchElementException when trying to perform an operation on an empty stack
     * @throws ArithmeticException when the stack of integers contains zeros
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Test
    public void testPushOperationLCM() throws NoSuchElementException, ArithmeticException, RemoteException {
        calculator.pushValue(1);
        calculator.pushValue(10);
        calculator.pushValue(100);
        calculator.pushOperation("lcm");
        assertEquals(100, calculator.pop());
    }

    /**
     * Test for delaying pop for single client
     * @throws RemoteException when exception is raised in the remote method call
     * @throws InterruptedException when a thread is interrupted before or during waiting, sleeping or occupied
     */
    @Test
    public void testDelayPop() throws RemoteException, InterruptedException {
        calculator.pushValue(50);
        assertEquals(50, calculator.delayPop(2000));
    }

    /**
     * Test for delaying pop with an invalid argument
     * @throws RemoteException when exception is raised in the remote method call
     * @throws IllegalArgumentException when an invalid argument is passed
     * @throws InterruptedException when a thread is interrupted before or during waiting, sleeping or occupied
     */
    @Test
    public void testDelayPopNegative() throws RemoteException, IllegalArgumentException, InterruptedException {
        calculator.pushValue(50);
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.delayPop(-2000);
        });
    }
}