package org.genfork.rpc.context.events;

import org.genfork.rpc.context.SyncConfigurationProperties.Outbound;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class SyncServiceForceInitEvent extends AbstractSyncServiceInitEvent {
	public static SyncServiceForceInitEvent build(Class<?> svcClass, Outbound outbound) {
		final String service = outbound.getService();
		final String host = outbound.getHost();
		final int port = outbound.getPort();
		return build(svcClass, service, host, port);
	}

	public static SyncServiceForceInitEvent build(Class<?> svcClass, String syncServiceName, String syncHost, int syncPort) {
		return new SyncServiceForceInitEvent(new ConnectedServiceProperties(svcClass, syncServiceName, syncHost, syncPort));
	}

	public SyncServiceForceInitEvent(ConnectedServiceProperties connectedServiceProperties) {
		super(connectedServiceProperties);
	}
}
