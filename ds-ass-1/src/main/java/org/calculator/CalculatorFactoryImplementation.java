package org.calculator;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

/**
 * Implementation class for calculator factory
 */
public class CalculatorFactoryImplementation extends UnicastRemoteObject implements CalculatorFactory {

    /**
     * Default constructor:
     * Calling super() automatically exports the UnicastRemoteObject so that it can
     * (1) allocate necessary resources for the object to participate in RMI
     * (2) assign the object a port to listen for RMIs
     * (3) returns a stub (proxy that handles communication between client and remote object
     * @throws RemoteException when exception is raised in the remote method call
     */
    protected CalculatorFactoryImplementation() throws RemoteException {
        super();
    }

    /**
     * Creates an instance of Calculator
     * @return a Calculator instance
     * @throws RemoteException when exception is raised in the remote method call
     */
    @Override
    public Calculator createCalculator() throws RemoteException {
        return CalculatorImplementation.createInstance();
    }
}
