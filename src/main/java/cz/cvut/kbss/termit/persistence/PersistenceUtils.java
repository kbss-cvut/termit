package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.metamodel.Metamodel;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

@Component
public class PersistenceUtils {

    private final WorkspaceMetadataProvider workspaceMetadataProvider;

    private final EntityManagerFactory emf;

    @Autowired
    public PersistenceUtils(WorkspaceMetadataProvider workspaceMetadataProvider, EntityManagerFactory emf) {
        this.workspaceMetadataProvider = workspaceMetadataProvider;
        this.emf = emf;
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

    /**
     * Gets JOPA metamodel.
     *
     * @return Metamodel of the persistence unit
     */
    public Metamodel getMetamodel() {
        return emf.getMetamodel();
    }
}
