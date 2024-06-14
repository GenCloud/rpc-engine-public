package org.genfork.rpc.data;

import lombok.Data;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Data
public class LockRequest implements RequestMessage {
	public static final long DEFAULT_LEASE_TIME = 300_000L;

	private String requestId;

	private LockRequestType lockRequestType;

	private long threadId;

	private String entry;

	private long waitTime;

	private long leaseTime;

	public LockRequest() {}

	public LockRequest(String requestId, LockRequestType lockRequestType, long threadId, String entry, long waitTime, long leaseTime) {
		this.requestId = requestId;
		this.lockRequestType = lockRequestType;
		this.threadId = threadId;
		this.entry = entry;
		this.waitTime = waitTime;
		this.leaseTime = leaseTime;
	}
}
