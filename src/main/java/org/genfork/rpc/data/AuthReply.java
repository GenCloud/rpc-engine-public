package org.genfork.rpc.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Data
@NoArgsConstructor
public class AuthReply implements RequestMessage {
	private String requestId;
	private boolean success;
	private String reason;

	public AuthReply(String requestId, boolean success, String reason) {
		this.requestId = requestId;
		this.success = success;
		this.reason = reason;
	}
}
