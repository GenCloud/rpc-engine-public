package org.genfork.rpc.context.events;

import org.springframework.context.ApplicationEvent;

/**
 * @author: GenCloud
 * @date: 2024/01
 */
public abstract class AbstractSyncServiceInitEvent extends ApplicationEvent {
	public AbstractSyncServiceInitEvent(ConnectedServiceProperties connectedServiceProperties) {
		super(connectedServiceProperties);
	}

	@Override
	public ConnectedServiceProperties getSource() {
		return (ConnectedServiceProperties) super.getSource();
	}

	public record ConnectedServiceProperties(Class<?> svcClass, String syncServiceName, String syncHost, int syncPort) {
	}
}
