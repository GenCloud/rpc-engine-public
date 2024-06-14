package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class RemoteServiceConnectionException extends RemoteException {
	public RemoteServiceConnectionException(String message) {
		super(message);
	}

	public RemoteServiceConnectionException(String message, Throwable cause) {
		super(message, cause);
	}
}
