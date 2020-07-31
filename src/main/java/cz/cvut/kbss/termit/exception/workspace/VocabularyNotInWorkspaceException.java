package cz.cvut.kbss.termit.exception.workspace;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Workspace;

import java.net.URI;

/**
 * Indicates that an attempt was made to access a vocabulary that has not been imported to a workspace.
 */
public class VocabularyNotInWorkspaceException extends TermItException {

    public VocabularyNotInWorkspaceException(String message) {
        super(message);
    }

    public static VocabularyNotInWorkspaceException create(URI vocabularyId, Workspace workspace) {
        return new VocabularyNotInWorkspaceException(
                "Vocabulary <" + vocabularyId + "> does not exist in workspace " + workspace);
    }
}
