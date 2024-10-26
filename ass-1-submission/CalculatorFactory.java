package org.calculator;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface for creating a calculator factory which will be bounded to the RMI registry
 */
public interface CalculatorFactory extends Remote {
    /**
     * Creates a Calculator instance for the client that calls the method
     * @return a Calculator instance
     * @throws RemoteException when exception is raised in the remote method call
     */
    Calculator createCalculator() throws RemoteException;
}
