package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.IdentifierChangeRecord_;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Repository
public class IriMigrationDao {
    private final EntityManager em;
    private final DescriptorFactory descriptorFactory;

    public IriMigrationDao(EntityManager em, DescriptorFactory descriptorFactory) {
        this.em = em;
        this.descriptorFactory = descriptorFactory;
    }

    /**
     * Replaces any occurrence of the {@link IriMigrationPair#originalIri()} with the {@link IriMigrationPair#newIri()}.
     *
     * <p>Snapshot modification is prevented except of rewiring the {@code is-version-of} relations.
     *
     * @param iris the pair of IRIs for migration
     */
    public void migrateIdentifier(IriMigrationPair iris) {
        try {
            Query namedGraphQuery = constructMigrationQuery(iris, false);
            namedGraphQuery.executeUpdate();

            Query defaultGraphQuery = constructMigrationQuery(iris, true);
            defaultGraphQuery.setParameter("graph", Constants.DEFAULT_GRAPH);
            defaultGraphQuery.executeUpdate();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to migrate identifier: " + iris, e);
        }
    }

    private Query constructMigrationQuery(IriMigrationPair iris, boolean defaultGraph) {
        String insertPattern = "?newSubject ?newPredicate ?newObject .";
        if (!defaultGraph) {
            insertPattern = "GRAPH ?graph { " + insertPattern + " }";
        }
        final String queryString =
                Utils.loadQuery("identifierMigration.rq").replace("##$INSERT_PATTERN$##", insertPattern);
        Query query = em.createNativeQuery(queryString);
        bind(query, iris);
        return query;
    }

    /**
     * Retrieves all types of the given entity.
     *
     * @param entityUri the entity identifier
     * @return the list of distinct types of the entity
     */
    public List<URI> getEntityTypes(URI entityUri) {
        Objects.requireNonNull(entityUri);
        try {
            return em.createNativeQuery("SELECT DISTINCT ?type WHERE { ?entity a ?type }", URI.class)
                    .setParameter("entity", entityUri)
                    .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to load entity types: " + Utils.uriToString(entityUri), e);
        }
    }

    /**
     * Moves the {@code originalGraph} to the {@code newGraph} if the original Graph exists. Does nothing if the
     * {@code originalGraph} does not exist.
     *
     * @param originalGraph the original graph to move
     * @param newGraph the destination where the original graph should be moved
     */
    public void moveGraph(URI originalGraph, URI newGraph) {
        Objects.requireNonNull(originalGraph);
        Objects.requireNonNull(newGraph);
        try {
            em.createNativeQuery("MOVE SILENT GRAPH ?original TO ?new")
                    .setParameter("original", originalGraph)
                    .setParameter("new", newGraph)
                    .executeUpdate();
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to move graph " + Utils.uriToString(originalGraph) + " to " + Utils.uriToString(newGraph),
                    e);
        }
    }

    /**
     * Replaces the preferred namespace of the vocabulary with the {@code namespace}.
     *
     * <p>Identifiers of the vocabulary terms are not changed.
     *
     * @param vocabularyUri the identifier of the vocabulary
     * @param namespace the new preferred namespace of the vocabulary
     */
    public void updatePreferredNamespace(URI vocabularyUri, String namespace) {
        Objects.requireNonNull(vocabularyUri);
        Objects.requireNonNull(namespace);
        final Descriptor vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabularyUri);
        try {
            final Vocabulary managed = em.find(Vocabulary.class, vocabularyUri, vocabularyDescriptor);
            managed.setPreferredNamespaceUri(namespace);
            em.merge(managed);
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to update preferred namespace of vocabulary: " + Utils.uriToString(vocabularyUri), e);
        }
    }

    public Stream<URI> findAllTerms(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        try {
            return em.createQuery("SELECT DISTINCT term FROM Term term WHERE term.vocabulary = :vocabulary", URI.class)
                    .setParameter("vocabulary", vocabularyUri)
                    .getResultStream();
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find all terms for vocabulary: " + Utils.uriToString(vocabularyUri), e);
        }
    }

    public void detach(Asset<?> entity) {
        em.detach(entity);
    }

    /**
     * Binds {@code ?originalIri} and {@code ?newIri} from the {@link IriMigrationPair}.
     *
     * @param query the query to bind parameters to
     * @param iris the pair of IRIs for migration
     */
    private static void bind(Query query, IriMigrationPair iris) {
        query.setParameter("originalIri", iris.originalIri())
                .setParameter("newIri", iris.newIri())
                .setParameter("isVersionOfRelations", Constants.IS_VERSION_OF_RELATIONS)
                .setParameter("identifierChangeType", IdentifierChangeRecord_.entityClassIRI);
    }

    public void evictCache() {
        em.getEntityManagerFactory().getCache().evictAll();
    }

    public void flushChanges() {
        em.flush();
        em.clear();
    }
}
