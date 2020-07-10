package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.termit.workspace.WorkspaceMetadataCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

@Component
public class PersistenceUtils {

    private final WorkspaceMetadataCache workspaceMetadataCache;

    @Autowired
    public PersistenceUtils(WorkspaceMetadataCache workspaceMetadataCache) {
        this.workspaceMetadataCache = workspaceMetadataCache;
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
        return workspaceMetadataCache.getCurrentWorkspaceMetadata().getVocabularyInfo(vocabularyUri).getContext();
    }
}
