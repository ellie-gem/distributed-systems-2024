package org.calculator;

import java.rmi.Naming;

/**
 * The test client having a Calculator instance created by the factory / server to test the remote methods
 */
public class CalculatorClient {
    public static void main(String[] args) {
        try{
            CalculatorFactory factory = (CalculatorFactory) Naming.lookup("rmi://localhost:5000/calculatorFactory");

            Calculator calculator = factory.createCalculator();

            System.out.println("Pushing values: 10, 20");
            calculator.pushValue(10);
            calculator.pushValue(20);
            calculator.pushOperation("min");
            System.out.println("Min: " + calculator.pop());

            System.out.println("Pushing values: 10, 20");
            calculator.pushValue(10);
            calculator.pushValue(20);
            calculator.pushOperation("max");
            System.out.println("Max: " + calculator.pop());

            System.out.println("Pushing values: 10, 20");
            calculator.pushValue(10);
            calculator.pushValue(20);
            calculator.pushOperation("lcm");
            System.out.println("LCM: " + calculator.pop());

            System.out.println("Pushing values: 10, 20, 30, 8, 6");
            calculator.pushValue(10);
            calculator.pushValue(20);
            calculator.pushValue(30);
            calculator.pushValue(8);
            calculator.pushValue(6);
            calculator.pushOperation("gcd");
            System.out.println("GCD: " + calculator.pop());

            System.out.println("Is stack empty? " + calculator.isEmpty());

            System.out.println("Pushing values: 30");
            calculator.pushValue(30);
            System.out.println("Is stack empty? " + calculator.isEmpty());

            System.out.println("Calling delayed pop");
            System.out.println("Delayed pop: " + calculator.delayPop(5000));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
