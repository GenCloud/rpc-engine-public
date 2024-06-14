package org.genfork.rpc.lock;

import org.genfork.rpc.annotations.ServiceId;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public interface LockSupport {
	default ClusterSyncLock getLock(@ServiceId String serviceId, String entry) {
		throw new UnsupportedOperationException();
	}

	default ClusterSyncLock getLock(String entry) {
		throw new UnsupportedOperationException();
	}
}
