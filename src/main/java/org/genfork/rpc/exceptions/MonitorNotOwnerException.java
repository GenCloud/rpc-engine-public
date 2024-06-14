package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class MonitorNotOwnerException extends RemoteException {
	public MonitorNotOwnerException(String message) {
		super(message);
	}
}
