package cz.cvut.kbss.termit.service.workspace;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collection;

/**
 * Manages workspace.
 * <p>
 * In some deployments, the user is able to edit only a specific set of vocabularies (more precisely, working copies of
 * the vocabularies). This service manages this process.
 */
@Service
public class WorkspaceService {

    /**
     * Opens the specified set of repository contexts for editing.
     * <p>
     * This assumes that the contexts contain vocabularies that are to be editable. All other vocabularies remain
     * read-only and attempting to edit them will result in an exception.
     * <p>
     * The specified contexts also override vocabulary contexts provided by default by {@link
     * cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper}, as they would normally point to the canonical
     * (non-editable) versions of the vocabularies, whereas the contexts provided to this method will contain editable
     * copies of the vocabularies.
     * <p>
     * Note that it is expected that the contexts already exist, it is not the responsibility of TermIt to create them
     * or populate them with any seed data.
     *
     * @param contexts Contexts to open for editing
     */
    public void openForEditing(Collection<URI> contexts) {

    }
}
