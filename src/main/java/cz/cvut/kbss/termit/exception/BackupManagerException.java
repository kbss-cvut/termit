package cz.cvut.kbss.termit.exception;

/**
 * Indicates an error when processing a backup of a file.
 */
public class BackupManagerException extends TermItException {

    public BackupManagerException(String message) {
        super(message);
    }

    public BackupManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
