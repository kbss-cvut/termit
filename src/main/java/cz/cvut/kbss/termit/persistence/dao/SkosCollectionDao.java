package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityGraph;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.query.QueryHints;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TypeAwareEntity;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.AbstractTerm_;
import cz.cvut.kbss.termit.model.Term_;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.persistence.dao.util.SparqlPatterns.orderSentence;

@Repository
public class SkosCollectionDao {

    /**
     * Matches all collections (both ordered and unordered) in a vocabulary.
     *
     * @implNote Bindings are applied by {@link #bindCollectionsInVocabulary(TypedQuery, Vocabulary)}
     */
    private static final String COLLECTIONS_IN_VOCABULARY_PATTERN = """
            GRAPH ?vocabulary {
                ?collection a ?type ;
                    ?inScheme ?vocabulary .
                FILTER(?type IN (?collectionTypes)) .
                ?collection ?hasLabel ?label .
                FILTER(lang(?label) = ?language)
            }
            """;

    /**
     * Matches collections and terms in a vocabulary that are not members of a collection (excluding the collection
     * itself) along with their label in the vocabulary language. Collections are bound to {@code ?priority} 0, terms to
     * 1.
     *
     * @implNote Bindings are applied by {@link #bindAddMemberOptions(Query, URI, Vocabulary, String)}
     */
    private static final String ADD_MEMBER_OPTIONS_PATTERN = """
            GRAPH ?vocabulary {
                {
                    # a different collection in vocabulary
                    ?entity a ?type .
                    FILTER(?type IN (?collectionTypes))
                    FILTER(?entity != ?collection)
                    ?entity ?hasCollectionLabel ?label .
                    BIND(0 AS ?priority)
                } UNION {
                    # a term in vocabulary
                    ?entity a ?type .
                    FILTER(?type = ?termType)
                    ?entity ?hasTermLabel ?label .
                    BIND(1 AS ?priority)
                }
                ?entity ?inScheme ?vocabulary .
                FILTER(lang(?label) = ?language)
            }
            # filter non-members
            FILTER NOT EXISTS {
                ?collection ?hasMember|(?hasMemberList/?listRest*/?listFirst) ?entity .
            }
            FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) .
            """;

    /** Property identifiers representing a label of collection member */
    private static final Set<URI> MEMBER_LABEL_PROPERTIES =
            Set.of(AbstractTerm_.labelPropertyIRI.toURI(), SkosCollection_.labelPropertyIRI.toURI());

    protected static final URI UNORDERED_URI = TermCollection_.entityClassIRI.toURI();
    protected static final URI ORDERED_URI = OrderedTermCollection_.entityClassIRI.toURI();

    private static final Set<URI> COLLECTION_TYPES = Set.of(ORDERED_URI, UNORDERED_URI);

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
     * <p>Collections are sorted by their label in the primary language of the vocabulary. The sort direction is taken
     * from the {@code label} property of the page sort specification, ascending by default.
     *
     * @param vocabulary the vocabulary of the collections
     * @return Page of collection DTOs
     */
    public Page<SkosCollectionDto> findAll(Vocabulary vocabulary, Pageable pageable) {
        Objects.requireNonNull(vocabulary.getUri(), "Vocabulary URI must not be null");
        Objects.requireNonNull(vocabulary.getPrimaryLanguage(), "Vocabulary primary language must not be null");
        try {
            final List<SkosCollectionDto> data = bindCollectionsInVocabulary(
                            em.createNativeQuery(
                                    """
                                    SELECT DISTINCT ?collection WHERE {
                                    """ + COLLECTIONS_IN_VOCABULARY_PATTERN + """
                                    } ORDER BY
                                    """
                                            + buildSortClause(pageable, "?label", "?collection"),
                                    SkosCollectionDto.class),
                            vocabulary)
                    .setDescriptor(descriptor(vocabulary))
                    .setFirstResult((int) pageable.getOffset())
                    .setMaxResults(pageable.getPageSize())
                    .getResultList();
            return PageableExecutionUtils.getPage(data, pageable, () -> countAll(vocabulary));
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find all SKOS collections in Vocabulary " + Utils.uriToString(vocabulary.getUri()), e);
        }
    }

    /**
     * Counts all collections (both ordered and unordered) in the given vocabulary.
     *
     * @param vocabulary the vocabulary of the collections
     * @return the total number of collections
     */
    private long countAll(Vocabulary vocabulary) {
        return bindCollectionsInVocabulary(
                        em.createNativeQuery(
                                "SELECT (COUNT(DISTINCT ?collection) AS ?count) WHERE { "
                                        + COLLECTIONS_IN_VOCABULARY_PATTERN + " }",
                                Long.class),
                        vocabulary)
                .getSingleResult();
    }

    private <T> TypedQuery<T> bindCollectionsInVocabulary(TypedQuery<T> query, Vocabulary vocabulary) {
        return query.setParameter("vocabulary", vocabulary.getUri())
                .setParameter("inScheme", SkosCollection_.vocabularyPropertyIRI)
                .setParameter("collectionTypes", COLLECTION_TYPES)
                .setParameter("hasLabel", SkosCollection_.labelPropertyIRI.toURI())
                .setParameter("language", vocabulary.getPrimaryLanguage());
    }

    public SkosCollectionDto findDto(URI collectionUri) {
        try {
            return em.find(SkosCollectionDto.class, collectionUri);
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to find SKOS collection DTO " + Utils.uriToString(collectionUri), e);
        }
    }

    /**
     * Retrieves the collection of the requested type.
     *
     * @param clazz the type of the collection
     * @param collectionUri the identifier of the collection
     * @param vocabulary the vocabulary of the collection
     * @return the collection or {@code null} when not found
     * @param <T> {@link TermCollection} or {@link OrderedTermCollection}
     * @implNote Note that ordered collection can be represented as unordered, but its updates are in such case
     *     prohibited.
     */
    public <T extends SkosCollection<?>> T find(Class<T> clazz, URI collectionUri, Vocabulary vocabulary) {
        try {
            return em.find(clazz, collectionUri, descriptor(vocabulary));
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find SKOS collection " + Utils.uriToString(collectionUri) + " using type "
                            + clazz.getSimpleName(),
                    e);
        }
    }

    /**
     * Resolves a page of members of the ordered collection along with their type.
     *
     * @param collection the collection whose members should be resolved
     * @param pageable page specification
     * @return members of the requested page in the order of the collection
     */
    public List<TypeAwareEntity> findMembers(OrderedTermCollection collection, Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
            throw new InvalidParameterException("Page specification must be paged and unsorted! "
                    + "Ordered collections are always sorted by the collection order.");
        }
        try {
            final List<URI> identifiers = collection.getMembers().stream()
                    .skip(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .toList();
            return findEntitiesWithType(identifiers);
        } catch (TermItException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find typed members of ordered SKOS collection " + Utils.uriToString(collection.getUri()),
                    e);
        }
    }

    /**
     * Resolves a page of members of the unordered collection along with their type.
     *
     * <p>Members are sorted by their label ({@link SKOS#PREF_LABEL} of terms, {@link DC.Terms#TITLE} of collections).
     * Members with a label in the specified language are placed first. The sort direction is taken from the
     * {@code label} property of the page sort specification, ascending by default.
     *
     * @param collection the collection whose members should be resolved
     * @param pageable page specification
     * @param language language of the labels used for sorting
     * @return members of the requested page sorted by their label
     */
    public List<TypeAwareEntity> findMembers(TermCollection collection, Pageable pageable, String language) {
        Objects.requireNonNull(language, "Language must not be null");
        try {
            final List<URI> identifiers = em.createNativeQuery(
                            """
                            SELECT DISTINCT ?member WHERE {
                                ?collection ?hasMember ?member .
                                OPTIONAL {
                                    # label in primary language
                                    ?member ?primaryLabelProperty ?primaryLabel .
                                    FILTER(?primaryLabelProperty IN (?labelProperties))
                                    FILTER(lang(?primaryLabel) = ?language)
                                }
                                OPTIONAL {
                                    ?member ?anyLabelProperty ?anyLabel .
                                    FILTER(?anyLabelProperty IN (?labelProperties))
                                }
                                BIND(COALESCE(?primaryLabel, ?anyLabel) AS ?sortLabel)
                                BIND(IF(BOUND(?primaryLabel), 1, 0) AS ?hasLocaleLabel)
                            } ORDER BY DESC(?hasLocaleLabel)
                            """ + buildSortClause(pageable, "?sortLabel", "?member"), URI.class)
                    .setParameter("collection", collection.getUri())
                    .setParameter("hasMember", TermCollection_.membersPropertyIRI.toURI())
                    .setParameter("labelProperties", MEMBER_LABEL_PROPERTIES)
                    .setParameter("language", language)
                    .setFirstResult((int) pageable.getOffset())
                    .setMaxResults(pageable.getPageSize())
                    .getResultList();
            return findEntitiesWithType(identifiers);
        } catch (TermItException e) {

            throw e;
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find typed members of SKOS collection " + Utils.uriToString(collection.getUri()), e);
        }
    }

    /**
     * Builds the sort clause for {@link #findAll(Vocabulary, Pageable)} and {@link #findMembers(TermCollection,
     * Pageable, String)}, sorting by the label and then by the entity identifier in the direction requested for the
     * {@code label} property.
     *
     * @param label the variable representing the label used for sorting, e.g. {@code "?sortLabel"}
     * @param entity the variable representing the sorted entity, e.g. {@code "?member"}
     */
    private static String buildSortClause(Pageable pageable, String label, String entity) {
        final Sort.Order labelOrder = pageable.getSort().getOrderFor("label");
        final String direction = labelOrder != null && labelOrder.isDescending() ? "DESC" : "ASC";
        return direction + "(" + orderSentence("STR(" + label + ")") + ") " + direction + "(" + entity + ")";
    }

    /**
     * Resolves types of the given entities.
     *
     * @param identifiers identifiers of the entities
     * @return entities in the order of {@code identifiers}
     */
    public List<TypeAwareEntity> findEntitiesWithType(Collection<URI> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            final List<TypeAwareEntity> entities =
                    entitiesWithTypeQuery(identifiers).getResultList();
            if (entities.size() != identifiers.size()) {
                final Set<URI> foundEntities =
                        entities.stream().map(TypeAwareEntity::getUri).collect(Collectors.toSet());
                final String missing = identifiers.stream()
                        .filter(Predicate.not(foundEntities::contains))
                        .map(Utils::uriToString)
                        .collect(Collectors.joining(", "));
                throw new NotFoundException("Resource(s) not found: " + missing);
            }
            // map of entity URI to the type aware entity
            final Map<URI, TypeAwareEntity> byUri =
                    entities.stream().collect(Collectors.toMap(TypeAwareEntity::getUri, Function.identity()));
            // extract type aware entity from the map in the requested identifiers order
            return identifiers.stream()
                    .map(byUri::get)
                    .map(Objects::requireNonNull)
                    .toList();
        } catch (TermItException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find typed entities with identifiers: "
                            + identifiers.stream().map(Utils::uriToString).collect(Collectors.joining(", ")),
                    e);
        }
    }

    /**
     * Resolves the type of the given collection.
     *
     * @param collectionUri the identifier of the collection
     * @return {@link TermCollection} or {@link OrderedTermCollection}
     */
    public Class<? extends SkosCollection<?>> findCollectionType(URI collectionUri) {
        try {
            final TypeAwareEntity entity = (TypeAwareEntity)
                    entitiesWithTypeQuery(Set.of(collectionUri)).getSingleResult();
            if (entity != null) {
                if (entity.isOrderedCollection()) {
                    return OrderedTermCollection.class;
                }
                if (entity.isCollection()) {
                    return TermCollection.class;
                }
            }
        } catch (NoResultException e) {
            return null;
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find type for SKOS collection: " + Utils.uriToString(collectionUri), e);
        }
        return null;
    }

    private Query entitiesWithTypeQuery(Collection<URI> identifiers) {
        return em.createNativeQuery("""
                 SELECT ?entity (GROUP_CONCAT(DISTINCT STR(?type); SEPARATOR="%s") AS ?types) WHERE {
                     ?entity a ?type .
                     FILTER(?entity IN (?ids))
                 } GROUP BY ?entity
                 """.formatted(Constants.GROUP_CONCAT_SEPARATOR), TypeAwareEntity.RESULTSET_MAPPING)
                .setParameter("ids", identifiers);
    }

    public void update(SkosCollection<?> skosCollection, Vocabulary vocabulary) {
        try {
            em.merge(skosCollection, descriptor(vocabulary));
            em.getEntityManagerFactory()
                    .getCache()
                    .evict(SkosCollectionDto.class, skosCollection.getUri(), vocabulary.getUri());
            em.getEntityManagerFactory().getCache().evict(SkosCollectionDto.class, skosCollection.getUri(), null);
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to update SKOS collection " + Utils.uriToString(skosCollection.getUri()) + " in vocabulary "
                            + Utils.uriToString(vocabulary.getUri()),
                    e);
        }
    }

    public void persist(SkosCollection<?> skosCollection, Vocabulary vocabulary) {
        try {
            em.persist(skosCollection, descriptor(vocabulary));
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to persist SKOS collection " + Utils.uriToString(skosCollection.getUri())
                            + " in vocabulary " + Utils.uriToString(vocabulary.getUri()),
                    e);
        }
    }

    /**
     * Removes the specified collection from the context of the specified vocabulary.
     *
     * @param skosCollection the collection to remove
     * @param vocabulary the vocabulary of the collection
     */
    public void remove(SkosCollection<?> skosCollection, Vocabulary vocabulary) {
        Objects.requireNonNull(skosCollection.getUri(), "Collection identifier must not be null!");
        try {
            em.remove(skosCollection);
            em.getEntityManagerFactory()
                    .getCache()
                    .evict(SkosCollectionDto.class, skosCollection.getUri(), vocabulary.getUri());
            em.getEntityManagerFactory().getCache().evict(SkosCollectionDto.class, skosCollection.getUri(), null);
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to remove SKOS collection " + Utils.uriToString(skosCollection.getUri())
                            + " from vocabulary " + Utils.uriToString(vocabulary.getUri()),
                    e);
        }
    }

    public boolean exists(URI collectionUri) {
        Objects.requireNonNull(collectionUri);
        try {
            return em.createNativeQuery("ASK { ?x a ?type . }", Boolean.class)
                    .setParameter("x", collectionUri)
                    .setParameter("type", TermCollection_.entityClassIRI)
                    .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to check existence of SKOS collection " + Utils.uriToString(collectionUri), e);
        }
    }

    /**
     * Finds possible collection members (Terms or other Collections) from the vocabulary that are not already present
     * in the collection.
     *
     * <p>All collections are placed before terms, both are sorted by their label. The sort direction is taken from the
     * {@code label} property of the page sort specification, ascending by default.
     *
     * @param collectionUri the collection whose members should be excluded
     * @param vocabulary vocabulary of the collection
     * @param searchString string by which to filter the entities (label), may be {@code null}
     * @param pageRequest page request
     * @return Page of entities with their types that can be added to the collection
     */
    public Page<TypeAwareEntity> findAddMemberOptions(
            URI collectionUri, Vocabulary vocabulary, String searchString, Pageable pageRequest) {
        Objects.requireNonNull(collectionUri, "Collection identifier must not be null");
        Objects.requireNonNull(vocabulary.getUri(), "Vocabulary URI must not be null");
        Objects.requireNonNull(vocabulary.getPrimaryLanguage(), "Vocabulary primary language must not be null");
        try {
            final Query query = em.createNativeQuery(
                    """
                    SELECT ?entity (GROUP_CONCAT(DISTINCT STR(?type); SEPARATOR="%s") AS ?types) WHERE {
                    """.formatted(Constants.GROUP_CONCAT_SEPARATOR)
                            + ADD_MEMBER_OPTIONS_PATTERN + """
                    } GROUP BY ?entity ?priority ?label
                    ORDER BY ASC(?priority)
                    """ + buildSortClause(pageRequest, "?label", "?entity"),
                    TypeAwareEntity.RESULTSET_MAPPING);
            bindAddMemberOptions(query, collectionUri, vocabulary, searchString);
            @SuppressWarnings("unchecked")
            final List<TypeAwareEntity> data = query.setFirstResult((int) pageRequest.getOffset())
                    .setMaxResults(pageRequest.getPageSize())
                    .getResultList();
            return PageableExecutionUtils.getPage(
                    data, pageRequest, () -> countAddMemberOptions(collectionUri, vocabulary, searchString));
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to find add member options for SKOS collection " + Utils.uriToString(collectionUri), e);
        }
    }

    /**
     * Counts all possible collection members matched by {@link #findAddMemberOptions(URI, Vocabulary, String,
     * Pageable)}.
     */
    private long countAddMemberOptions(URI collectionUri, Vocabulary vocabulary, String searchString) {
        final TypedQuery<Long> query = em.createNativeQuery(
                "SELECT (COUNT(DISTINCT ?entity) AS ?count) WHERE { " + ADD_MEMBER_OPTIONS_PATTERN + " }", Long.class);
        bindAddMemberOptions(query, collectionUri, vocabulary, searchString);
        return query.getSingleResult();
    }

    private void bindAddMemberOptions(Query query, URI collectionUri, Vocabulary vocabulary, String searchString) {
        if (searchString == null) {
            searchString = "";
        }
        query.setParameter("collection", collectionUri)
                .setParameter("hasMember", TermCollection_.membersPropertyIRI)
                .setParameter("hasMemberList", OrderedTermCollection_.membersPropertyIRI)
                .setParameter("listRest", URI.create(RDF.REST))
                .setParameter("listFirst", URI.create(RDF.FIRST))
                .setParameter("vocabulary", vocabulary.getUri())
                .setParameter("inScheme", SkosCollection_.vocabularyPropertyIRI)
                .setParameter("language", vocabulary.getPrimaryLanguage())
                .setParameter("collectionTypes", COLLECTION_TYPES)
                .setParameter("hasCollectionLabel", SkosCollection_.labelPropertyIRI)
                .setParameter("termType", Term_.entityClassIRI)
                .setParameter("hasTermLabel", Term_.labelPropertyIRI)
                .setParameter("searchString", searchString);
    }

    public List<SkosCollectionDto> findCollectionsWithMember(URI memberUri) {
        Objects.requireNonNull(memberUri, "Member URI must not be null");
        try {
            EntityGraph<SkosCollectionDto> graph = em.createEntityGraph(SkosCollectionDto.class);
            graph.addAttributeNodes("vocabulary");
            return em.createNativeQuery(
                            """
                    SELECT DISTINCT ?collection ?vocabulary (GROUP_CONCAT(DISTINCT STR(?type); SEPARATOR="%s") AS ?types) WHERE {
                        GRAPH ?vocabulary {
                            ?collection a ?type ;
                                ?hasMember|(?hasMemberList/?listRest*/?listFirst) ?member ;
                                ?inVocabulary ?vocabulary .
                            FILTER(?type IN (?collectionTypes)) .
                        }
                    } GROUP BY ?collection ?vocabulary
                    """.formatted(Constants.GROUP_CONCAT_SEPARATOR),
                            SkosCollectionDto.COLLECTION_WITH_VOCABULARY_MAPPING)
                    .setParameter("hasMember", TermCollection_.membersPropertyIRI)
                    .setParameter("hasMemberList", OrderedTermCollection_.membersPropertyIRI)
                    .setParameter("listRest", URI.create(RDF.REST))
                    .setParameter("listFirst", URI.create(RDF.FIRST))
                    .setParameter("collectionTypes", COLLECTION_TYPES)
                    .setParameter("member", memberUri)
                    .setParameter("inVocabulary", SkosCollection_.vocabularyPropertyIRI)
                    .setHint(QueryHints.FETCH_GRAPH, graph)
                    .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to find collections with member " + Utils.uriToString(memberUri), e);
        }
    }
}
