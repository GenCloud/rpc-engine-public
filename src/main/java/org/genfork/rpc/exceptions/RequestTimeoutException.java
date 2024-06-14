package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class RequestTimeoutException extends RemoteException {
	public RequestTimeoutException(String message) {
		super(message);
	}
}
