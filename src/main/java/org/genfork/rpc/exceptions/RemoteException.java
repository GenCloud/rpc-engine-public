package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class RemoteException extends RuntimeException {
	public RemoteException(String message){
		super(message);
	}

	public RemoteException(Throwable cause) {
		super(cause);
	}

	public RemoteException(String message, Throwable cause) {
		super(message, cause);
	}
}
