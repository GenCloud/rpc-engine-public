package org.genfork.rpc.context.events;

import org.genfork.rpc.context.SyncConfigurationProperties;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class SyncServiceConnectedEvent extends AbstractSyncServiceInitEvent {
	public static SyncServiceConnectedEvent build(Class<?> svcClass, SyncConfigurationProperties.Outbound outbound) {
		final String service = outbound.getService();
		final String host = outbound.getHost();
		final int port = outbound.getPort();
		return build(svcClass, service, host, port);
	}

	public static SyncServiceConnectedEvent build(Class<?> svcClass, String syncServiceName, String syncHost, int syncPort) {
		return new SyncServiceConnectedEvent(new ConnectedServiceProperties(svcClass, syncServiceName, syncHost, syncPort));
	}

	public SyncServiceConnectedEvent(ConnectedServiceProperties connectedServiceProperties) {
		super(connectedServiceProperties);
	}
}
