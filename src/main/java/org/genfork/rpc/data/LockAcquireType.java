package org.genfork.rpc.data;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public enum LockAcquireType {
	SUCCESS,
	RR_WAIT_TIMEOUT,
	ALREADY_LOCKED,
	RELEASED,
	NOT_OWNED
}
