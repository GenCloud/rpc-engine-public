package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class RemoteServiceTimeoutException extends RemoteException {
	public RemoteServiceTimeoutException(String message) {
		super(message);
	}

	public RemoteServiceTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}
