package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class BindException extends RemoteException {
	public BindException(String message){
		super(message);
	}

	public BindException(String message, Throwable th){
		super(message, th);
	}
}
