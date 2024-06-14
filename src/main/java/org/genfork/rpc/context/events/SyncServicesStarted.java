package org.genfork.rpc.context.events;

import org.springframework.context.ApplicationEvent;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class SyncServicesStarted extends ApplicationEvent {
	public SyncServicesStarted() {
		super("started");
	}
}
