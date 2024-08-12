package org.calculator;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

/**
 * The server / CalculatorFactory that binds to the RMI registry and allows remote method calls to be made
 */
public class CalculatorServer {
    public static void main(String[] args) {
        try {
            // Set up RMI registry and bind the factory
            // Don't need to explicitly call UnicastRemoteObject.exportObject(factory, 0); due to calling super() in constructor
            LocateRegistry.createRegistry(5000);
            CalculatorFactory factory = new CalculatorFactoryImplementation();

            // Bind object to RMI Registry (use rebind for automatically replacing existing stub with new one)
            Naming.rebind("rmi://localhost:5000/calculatorFactory", factory);

            System.out.println("CalculatorFactory server is ready.");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
