package org.genfork.rpc.exceptions;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class AuthenticationException extends RemoteException {
	public AuthenticationException(String message) {
		super(message);
	}
}
