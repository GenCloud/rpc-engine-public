package org.genfork.rpc.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Data
@NoArgsConstructor
public class DefaultReply implements RequestMessage {
	private String requestId;
	private Object result;

	public DefaultReply(String requestId, Object result) {
		this.requestId = requestId;
		this.result = result;
	}
}
