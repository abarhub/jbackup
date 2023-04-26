package org.jbackup.jbackup.exception;

public class ShadowCopyException extends JBackupException {

    public ShadowCopyException(String message) {
        super(message);
    }

    public ShadowCopyException(String message, Throwable cause) {
        super(message, cause);
    }
}
