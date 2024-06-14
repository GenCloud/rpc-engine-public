package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class ShutdownException extends RemoteException {
	public ShutdownException(String message) {
		super(message);
	}
}
