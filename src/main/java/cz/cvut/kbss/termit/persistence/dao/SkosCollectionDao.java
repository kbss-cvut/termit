package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TypeAwareEntity;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection_;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection_;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection_;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class SkosCollectionDao {
    protected final Class<TermCollection> unorderedType = TermCollection.class;
    protected final Class<OrderedTermCollection> orderedType = OrderedTermCollection.class;

    protected final URI unorderedUri = TermCollection_.entityClassIRI.toURI();
    protected final URI orderedUri = OrderedTermCollection_.entityClassIRI.toURI();

    private final EntityManager em;

    private final DescriptorFactory descriptorFactory;

    public SkosCollectionDao(EntityManager em, DescriptorFactory descriptorFactory) {
        this.em = em;
        this.descriptorFactory = descriptorFactory;
    }

    private Descriptor descriptor(Vocabulary vocabulary) {
        return descriptorFactory.assetDescriptor(vocabulary.getUri());
    }

    /**
     * Finds all collections (both ordered and unordered) in the given vocabulary.
     *
     * @param vocabulary the vocabulary of the collections
     * @return List of collection DTOs
     */
    public List<SkosCollectionDto> findAll(Vocabulary vocabulary, Pageable pageable) {
        Objects.requireNonNull(vocabulary.getUri(), "Vocabulary URI must not be null");
        try {
            return em.createNativeQuery("""
                    SELECT DISTINCT ?collection WHERE {
                        ?collection a ?type ;
                            ?inScheme ?vocabulary .
                        FILTER(?type IN (?collectionTypes)) .
                    }
                    """, SkosCollectionDto.class)
                    .setParameter("vocabulary", vocabulary.getUri())
                    .setParameter("inScheme", SkosCollection_.vocabularyPropertyIRI)
                    .setParameter("collectionTypes", Set.of(orderedUri, unorderedUri))
                    .setFirstResult((int) pageable.getOffset())
                    .setMaxResults(pageable.getPageSize())
                    .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find all SKOS collections in Vocabulary " + Utils.uriToString(vocabulary.getUri()), e);
        }
    }

    public SkosCollectionDto findDto(URI collectionUri) {
        try {
            return em.find(SkosCollectionDto.class, collectionUri);
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to find SKOS collection DTO " + Utils.uriToString(collectionUri), e);
        }
    }

    /**
     * Finds collection with the given identifier and returns its representation as unordered collection.
     *
     * @param unorderedCollectionUri collection identifier
     * @return the unordered collection representation
     * @implNote Note that ordered collection can be represented as unordered collection too
     */
    public TermCollection findUnordered(URI unorderedCollectionUri, Vocabulary vocabulary) {
        try {
            return em.find(unorderedType, unorderedCollectionUri, descriptor(vocabulary));
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find unordered term collection " + Utils.uriToString(unorderedCollectionUri), e);
        }
    }

    public OrderedTermCollection findOrdered(URI orderedCollectionUri, Vocabulary vocabulary) {
        try {
            return em.find(orderedType, orderedCollectionUri, descriptor(vocabulary));
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find ordered term collection " + Utils.uriToString(orderedCollectionUri), e);
        }
    }

    /** @implSpec The stream must be closed */
    public Stream<TypeAwareEntity> findMembers(SkosCollection<?> collection, Pageable pageable) {
        try {
            final Set<URI> identifiers = collection.getMembers().stream()
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toSet());
            return em.createNativeQuery(
                            """
                    SELECT ?entity (GROUP_CONCAT(DISTINCT STR(?type); SEPARATOR="%s") AS ?types) WHERE {
                        ?entity a ?type .
                        FILTER(?entity IN (?ids))
                    } GROUP BY ?entity
                    """.formatted(Constants.GROUP_CONCAT_SEPARATOR), TypeAwareEntity.RESULTSET_MAPPING)
                    .setParameter("ids", identifiers)
                    .getResultStream();
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find typped members of ordered SKOS collection "
                            + Utils.uriToString(collection.getUri()),
                    e);
        }
    }
}
