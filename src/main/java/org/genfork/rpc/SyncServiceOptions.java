package org.genfork.rpc;

import lombok.Getter;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class SyncServiceOptions {
	@Getter private Long executionTimeoutInMillis = 60_000L;

	private SyncServiceOptions() {
	}

	public static SyncServiceOptions defaults() {
		return new SyncServiceOptions();
	}

	public SyncServiceOptions expectResultWithin(long executionTimeoutInMillis) {
		this.executionTimeoutInMillis = executionTimeoutInMillis;
		return this;
	}

	public SyncServiceOptions noResult() {
		executionTimeoutInMillis = null;
		return this;
	}

	public boolean isResultExpected() {
		return executionTimeoutInMillis != null;
	}
}
