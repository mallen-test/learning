package org.mallen.test.learning.dl.zookeeper;

/**
 * @author mallen
 * @date 2/25/20
 */
public class LockingException extends RuntimeException {
    public LockingException(String message) {
        super(message);
    }

    public LockingException(String message, Throwable cause) {
        super(message, cause);
    }
}