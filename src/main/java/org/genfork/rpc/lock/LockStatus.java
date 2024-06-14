package org.genfork.rpc.lock;

import lombok.Getter;

import java.util.Objects;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Getter
public class LockStatus {
	private final long threadId;
	private final long leaseTime;

	public LockStatus(long threadId, long leaseTime) {
		this.threadId = threadId;
		this.leaseTime = leaseTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LockStatus status = (LockStatus) o;
		return threadId == status.threadId && leaseTime == status.leaseTime;
	}

	@Override
	public int hashCode() {
		return Objects.hash(threadId, leaseTime);
	}

	@Override
	public String toString() {
		return "LockStatus{" +
				"threadId=" + threadId +
				", leaseTime=" + leaseTime +
				'}';
	}
}
