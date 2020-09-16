package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

@Component
public class PersistenceUtils {

    private final WorkspaceMetadataProvider workspaceMetadataProvider;

    @Autowired
    public PersistenceUtils(WorkspaceMetadataProvider workspaceMetadataProvider) {
        this.workspaceMetadataProvider = workspaceMetadataProvider;
    }

    /**
     * Gets the current workspace's identifier.
     *
     * @return Current workspace IRI
     */
    public URI getCurrentWorkspace() {
        return workspaceMetadataProvider.getCurrentWorkspace().getUri();
    }

    /**
     * Determines the identifier of the repository context (named graph) in which vocabulary with the specified
     * identifier is stored.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Repository context identifier
     */
    public URI resolveVocabularyContext(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return workspaceMetadataProvider.getCurrentWorkspaceMetadata().getVocabularyInfo(vocabularyUri).getContext();
    }
}
