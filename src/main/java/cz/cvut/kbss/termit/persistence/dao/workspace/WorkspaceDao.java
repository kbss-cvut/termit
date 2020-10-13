package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class WorkspaceDao {

    private final EntityManager em;

    @Autowired
    public WorkspaceDao(EntityManager em) {
        this.em = em;
    }

    /**
     * Finds workspace with the specified identifier.
     *
     * @param id Workspace identifier
     * @return {@link Optional} containing the loaded workspace, or an empty optional if the workspace is not found.
     */
    public Optional<Workspace> find(URI id) {
        Objects.requireNonNull(id);
        return Optional.ofNullable(em.find(Workspace.class, id));
    }

    /**
     * Loads metadata about vocabularies available in the specified workspace.
     * <p>
     * These metadata contain, for example, references to contexts in which the vocabulary is stored in the specified
     * workspace.
     *
     * @param workspace Workspace to get vocabulary metadata for
     * @return List of vocabulary info objects
     */
    @SuppressWarnings("unchecked")
    public List<VocabularyInfo> findWorkspaceVocabularyMetadata(Workspace workspace) {
        return em.createNativeQuery("SELECT DISTINCT ?entity ?context ?changeTrackingContext WHERE {" +
                "?mc a ?metadataCtx ;" +
                "?references ?context ." +
                "?context a ?vocabularyCtx ." +
                "OPTIONAL {" +
                "?context ?hasChangeTrackingCtx ?changeTrackingContext ." +
                "?changeTrackingContext a ?changeTrackingCtx ." +
                "}" +
                "GRAPH ?context {" +
                "?entity a ?vocabulary ." +
                "}}", "VocabularyInfo").setParameter("mc", workspace.getUri())
                 .setParameter("metadataCtx", URI.create(Vocabulary.s_c_metadatovy_kontext))
                 .setParameter("references", URI.create(Vocabulary.s_p_odkazuje_na_kontext))
                 .setParameter("vocabularyCtx", URI.create(Vocabulary.s_c_slovnikovy_kontext))
                 .setParameter("hasChangeTrackingCtx", URI.create(Vocabulary.s_p_ma_kontext_sledovani_zmen))
                 .setParameter("changeTrackingCtx", URI.create(Vocabulary.s_c_kontext_sledovani_zmen))
                 .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik)).getResultList();
    }

    /**
     * Finds the specified user's current workspace.
     *
     * @param user User for which workspace should be retrieved
     * @return Current workspace of the specified user (if it is set)
     */
    public Optional<Workspace> findCurrentForUser(UserAccount user) {
        // TODO
        return Optional.empty();
    }
}
