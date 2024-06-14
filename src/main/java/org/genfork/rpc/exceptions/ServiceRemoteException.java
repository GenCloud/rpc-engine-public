package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class ServiceRemoteException extends RemoteException {
	public ServiceRemoteException(String message) {
		super(message);
	}

	public ServiceRemoteException(Throwable cause) {
		super(cause);
	}
}
