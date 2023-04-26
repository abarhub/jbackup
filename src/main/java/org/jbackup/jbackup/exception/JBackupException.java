package org.jbackup.jbackup.exception;

public class JBackupException extends RuntimeException{

    public JBackupException(String message) {
        super(message);
    }

    public JBackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
