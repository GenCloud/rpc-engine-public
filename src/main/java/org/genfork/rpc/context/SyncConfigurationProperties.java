package org.genfork.rpc.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@ConfigurationProperties(prefix = "server.sync")
@Configuration
@Data
public class SyncConfigurationProperties {
	private Transport transport = Transport.NIO;

	private Resources resources = new Resources();

	private Inbound inbound = new Inbound();

	private List<Outbound> outbound = new ArrayList<>(1);

	public Outbound findOutboundService(String serviceName) {
		return outbound.stream().filter(o -> o.getService().equals(serviceName)).findFirst().orElse(null);
	}

	@Data
	public static class Resources {
		private long lockLeaseTimeout = 300000L;
	}

	@Data
	public static class Inbound {
		private String host;
		private int port;
		private String login;
		private String password;
	}

	@Data
	public static class Outbound {
		private String service;
		private boolean enabled = true;
		private String host;
		private int port;
		private String login;
		private String password;
		private long timeout = 60_000; // -1 no result expected
		private long lockLeaseTimeout = 300000L;
		private int connections;
		private InitializeMode initializeMode;
	}

	public enum InitializeMode {
		ON_STARTUP,
		ON_CONNECT
	}
}
