package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class WaitLockTimeoutException extends RemoteException {
	public WaitLockTimeoutException(String message) {
		super(message);
	}
}
