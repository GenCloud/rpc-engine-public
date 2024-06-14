package org.genfork.rpc.data;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class LockRes {
	private final boolean success;

	public LockRes(boolean success) {
		this.success = success;
	}

	public boolean isSuccess() {
		return success;
	}
}
