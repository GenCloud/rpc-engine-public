package org.genfork.rpc.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Data
@NoArgsConstructor
public class AuthRequest implements RequestMessage {
	private String requestId, login, password;

	private long deadline = -1;

	public AuthRequest(String requestId, String login, String password) {
		this.requestId = requestId;
		this.login = login;
		this.password = password;
	}
}
