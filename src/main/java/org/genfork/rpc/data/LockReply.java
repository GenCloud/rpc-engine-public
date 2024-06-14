package org.genfork.rpc.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LockReply implements RequestMessage {
	private String requestId;
	private long threadId;
	private String entry;
	private LockAcquireType type;
}
