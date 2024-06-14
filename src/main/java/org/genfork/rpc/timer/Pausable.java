package org.genfork.rpc.timer;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public interface Pausable {
	/**
	 * Cancel this {@literal Pausable}. The implementing component should never react to any stimulus,
	 * closing resources if necessary.
	 *
	 * @return {@literal this}
	 */
	Pausable cancel();

	/**
	 * Pause this {@literal Pausable}. The implementing component should stop reacting, pausing resources if necessary.
	 *
	 * @return {@literal this}
	 */
	Pausable pause();

	/**
	 * Unpause this {@literal Pausable}. The implementing component should resume back from a previous pause,
	 * re-activating resources if necessary.
	 *
	 * @return {@literal this}
	 */
	Pausable resume();
}
