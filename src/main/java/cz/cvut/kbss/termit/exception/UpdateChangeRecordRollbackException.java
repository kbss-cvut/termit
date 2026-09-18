package cz.cvut.kbss.termit.exception;

/**
 * Indicates a failure during {@link cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord UpdateChangeRecord}
 * rollback
 */
public class UpdateChangeRecordRollbackException extends TermItException {
    public UpdateChangeRecordRollbackException() {
    }

    public UpdateChangeRecordRollbackException(String message) {
        super(message);
    }
}
