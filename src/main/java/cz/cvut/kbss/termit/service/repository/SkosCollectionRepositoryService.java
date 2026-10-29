package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TypeAwareEntity;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.SkosCollectionDao;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Validator;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class SkosCollectionRepositoryService {
    private final SkosCollectionDao collectionDao;
    private final TermRepositoryService termRepositoryService;
    private final Validator validator;

    public SkosCollectionRepositoryService(
            SkosCollectionDao collectionDao, TermRepositoryService termRepositoryService, Validator validator) {
        this.collectionDao = collectionDao;
        this.termRepositoryService = termRepositoryService;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public Page<SkosCollectionDto> findAll(
            Vocabulary vocabulary, String searchString, String searchLanguage, Pageable pageable) {
        return collectionDao.findAll(vocabulary, searchString, searchLanguage, pageable);
    }

    /**
     * Loads members of the collection and maps them to respective entity instances.
     *
     * @param collectionUri The collection identifier whose members should be loaded
     * @param pageable page specification
     * @return page of entities that are part of the collection, in the order of the collection
     * @see #resolveEntity(TypeAwareEntity)
     */
    @Transactional(readOnly = true)
    public Page<HasIdentifier> findMembers(URI collectionUri, Vocabulary vocabulary, Pageable pageable) {
        final SkosCollection<?> collection =
                find(collectionUri, vocabulary).orElseThrow(() -> notFound(collectionUri, vocabulary.getUri()));
        if (collection instanceof TermCollection unordered) {
            return findMembers(unordered, pageable);
        }
        if (collection instanceof OrderedTermCollection ordered) {
            return findMembers(ordered, pageable);
        }
        throw new IllegalStateException(
                "Unknown SKOS collection type: " + collection.getClass().getName());
    }

    /**
     * Loads members of the ordered collection and maps them to respective entity instances.
     *
     * @param collection The collection whose members should be loaded
     * @param pageable page specification
     * @return page of entities that are part of the collection, in the order of the collection
     * @see #resolveEntity(TypeAwareEntity)
     */
    @Transactional(readOnly = true)
    public Page<HasIdentifier> findMembers(OrderedTermCollection collection, Pageable pageable) {
        Objects.requireNonNull(collection, "Ordered SKOS collection must not be null");
        final List<HasIdentifier> data = resolveEntities(collectionDao.findMembers(collection, pageable));
        return new PageImpl<>(data, pageable, collection.getMembers().size());
    }

    /**
     * Loads members of the collection sorted by their label and maps them to respective entity instances.
     *
     * @param collection The collection whose members should be loaded
     * @param pageable page specification, the sort direction is taken from the {@code label} property
     * @return page of entities that are part of the collection
     * @see #resolveEntity(TypeAwareEntity)
     */
    @Transactional(readOnly = true)
    public Page<HasIdentifier> findMembers(TermCollection collection, Pageable pageable) {
        Objects.requireNonNull(collection, "SKOS Collection must not be null");
        final List<HasIdentifier> data = resolveEntities(collectionDao.findMembers(collection, pageable));
        return new PageImpl<>(data, pageable, collection.getMembers().size());
    }

    /** @see #resolveEntity(TypeAwareEntity) */
    private List<HasIdentifier> resolveEntities(List<TypeAwareEntity> entities) {
        return entities.stream()
                .map(this::resolveEntity)
                .map(Objects::requireNonNull)
                .toList();
    }

    /**
     * Finds SKOS collection by its identifier in the given vocabulary.
     *
     * @param collectionUri the identifier of the collection
     * @param vocabulary the vocabulary to which the collection belongs
     * @return the resolved {@link TermCollection} or {@link OrderedTermCollection} wrapped in an {@code Optional}
     */
    @Transactional(readOnly = true)
    public Optional<SkosCollection<?>> find(URI collectionUri, Vocabulary vocabulary) {
        return Optional.ofNullable(collectionDao.findCollectionType(collectionUri, vocabulary))
                .flatMap(type -> collectionDao.find(type, collectionUri, vocabulary));
    }

    @Transactional(readOnly = true)
    public Optional<SkosCollectionDto> findDto(URI collectionUri) {
        return collectionDao.findDto(collectionUri);
    }

    @Transactional(readOnly = true)
    public Optional<TermCollection> findUnordered(URI collectionUri, Vocabulary vocabulary) {
        return collectionDao.find(TermCollection.class, collectionUri, vocabulary);
    }

    @Transactional(readOnly = true)
    public Optional<OrderedTermCollection> findOrdered(URI collectionUri, Vocabulary vocabulary) {
        return collectionDao.find(OrderedTermCollection.class, collectionUri, vocabulary);
    }

    /**
     * Loads the respective entity object depending on the type of the entity.
     *
     * <p>Possible entity objects are:
     *
     * <ul>
     *   <li>{@link cz.cvut.kbss.termit.dto.TermInfo}
     *   <li>{@link cz.cvut.kbss.termit.dto.SkosCollectionDto}
     * </ul>
     *
     * @param entity the entity with its type
     * @return the loaded entity
     * @throws ValidationException if the entity is not Term or SKOS Collection
     */
    private HasIdentifier resolveEntity(TypeAwareEntity entity) {
        if (entity.isTerm()) {
            return termRepositoryService.findRequiredTermInfo(entity.getUri());
        }
        if (entity.isCollection()) {
            return collectionDao.findDto(entity.getUri()).orElseThrow(() -> notFound(entity.getUri(), null));
        }
        throw new ValidationException("Illegal entity in SKOS collection: " + Utils.uriToString(entity.getUri()));
    }

    private void preUpdate(SkosCollection<?> collection, Vocabulary vocabulary) {
        collectionDao.detach(collection);
        final SkosCollection<?> existing = find(collection.getUri(), vocabulary)
                .orElseThrow(() ->
                        new NotFoundException("Skos collection not found: " + Utils.uriToString(collection.getUri())));
        assert collection != existing;
        if (!existing.getVocabulary().equals(vocabulary.getUri())
                || !collection.getVocabulary().equals(vocabulary.getUri())) {
            throw new ValidationException("Vocabulary of SKOS collection cannot be changed!");
        }
        if (!collection.getClass().equals(existing.getClass())) {
            throw new ValidationException(
                    "Unable to update " + existing.getClass().getSimpleName() + " SKOS collection as "
                            + collection.getClass().getSimpleName());
        }
        if (collection.getPrimaryLanguage() == null) {
            collection.setPrimaryLanguage(existing.getPrimaryLanguage());
        }

        validate(collection, getAddedMembers(collection, existing), vocabulary);
    }

    private Set<URI> getAddedMembers(SkosCollection<?> updatedCollection, SkosCollection<?> existingCollection) {
        final Set<URI> existingMembers = new HashSet<>(existingCollection.getMembers());
        return updatedCollection.getMembers().stream()
                .filter(Predicate.not(existingMembers::contains))
                .collect(Collectors.toSet());
    }

    /**
     * Validates the given collection and the provided members.
     *
     * @param collection the collection to validate
     * @param members members that should be validated, allows to validate only a subset of collection members
     * @param vocabulary the vocabulary the members have to belong to
     */
    private void validate(SkosCollection<?> collection, Collection<URI> members, Vocabulary vocabulary) {
        BaseRepositoryService.validate(collection, validator);
        Objects.requireNonNull(collection.getUri(), "Collection identifier must not be null!");
        if (members != null && members.contains(collection.getUri())) {
            throw new ValidationException("Collection must not be member of itself!");
        }
        collectionDao
                .findEntitiesWithType(members, vocabulary)
                .forEach(SkosCollectionRepositoryService::ensureHasCorrectMemberType);
    }

    /**
     * Ensures that the given entity is valid member of SKOS Collection. Valid members are Concepts or another
     * Collections.
     *
     * @param entity the entity to check
     */
    private static void ensureHasCorrectMemberType(TypeAwareEntity entity) {
        Objects.requireNonNull(entity.getTypes(), "Entity must have types!");
        if (!entity.isTerm() && !entity.isCollection()) {
            throw new ValidationException("Illegal member in SKOS collection, The entity: "
                    + Utils.uriToString(entity.getUri()) + " is not allowed type.");
        }
    }

    @Transactional
    public void update(SkosCollection<?> skosCollection, Vocabulary vocabulary) {
        Objects.requireNonNull(skosCollection, "SKOS collection must not be null!");
        Objects.requireNonNull(vocabulary, "Vocabulary must not be null!");
        preUpdate(skosCollection, vocabulary);
        collectionDao.update(skosCollection, vocabulary);
    }

    @Transactional
    public void persist(SkosCollection<?> skosCollection, Vocabulary vocabulary) {
        Objects.requireNonNull(skosCollection, "SKOS collection must not be null!");
        Objects.requireNonNull(vocabulary, "Vocabulary must not be null!");
        validate(skosCollection, skosCollection.getMembers(), vocabulary);
        verifyIdentifierUnique(skosCollection);
        collectionDao.persist(skosCollection, vocabulary);
    }

    @Transactional
    public void remove(SkosCollection<?> skosCollection, Vocabulary vocabulary) {
        Objects.requireNonNull(skosCollection, "SKOS collection must not be null!");
        Objects.requireNonNull(vocabulary, "Vocabulary must not be null!");
        final SkosCollection<?> managed = collectionDao
                .find(skosCollection.getClass(), skosCollection.getUri(), vocabulary)
                .orElseThrow(() -> notFound(skosCollection.getUri(), vocabulary.getUri()));
        removeMemberFromAllCollections(managed.getUri());
        collectionDao.remove(managed, vocabulary);
    }

    @Transactional(readOnly = true)
    public boolean exists(URI collectionUri) {
        return collectionDao.exists(collectionUri);
    }

    private void verifyIdentifierUnique(SkosCollection<?> instance) {
        if (exists(instance.getUri())) {
            throw ResourceExistsException.create(instance.getClass().getSimpleName(), instance.getUri());
        }
    }

    @Transactional
    public void updateMemberPosition(
            OrderedTermCollection collection, Vocabulary vocabulary, URI memberIri, int originalPos, int newPos) {
        Objects.requireNonNull(collection.getUri(), "Collection identifier must not be null!");
        Objects.requireNonNull(collection.getMembers(), "Collection members must not be null!");
        Objects.requireNonNull(vocabulary.getUri(), "Vocabulary identifier must not be null!");
        Objects.requireNonNull(memberIri, "Member identifier must not be null!");
        if (originalPos < 0 || newPos < 0) {
            throw new ValidationException("Illegal member position in SKOS collection! Position must not be negative!");
        }
        if (originalPos == newPos) {
            return;
        }

        final List<URI> members = collection.getMembers();
        originalPos = Math.clamp(originalPos, 0, members.size() - 1);
        newPos = Math.clamp(newPos, 0, members.size() - 1); // the original position is removed from the list

        if (!members.get(originalPos).equals(memberIri)) {
            throw new ValidationException(
                    "Illegal member position in SKOS collection! Member not found at specified position!");
        }

        members.remove(originalPos);
        members.add(newPos, memberIri);
        update(collection, vocabulary);
    }

    /**
     * Loads entities that can be added to the collection and maps them to respective entity instances.
     *
     * @see SkosCollectionDao#findNonMembers(URI, Vocabulary, String, Pageable)
     * @see #resolveEntity(TypeAwareEntity)
     */
    @Transactional(readOnly = true)
    public Page<HasIdentifier> findNonMembers(
            URI collectionUri, Vocabulary vocabulary, String searchString, Pageable pageRequest) {
        return collectionDao
                .findNonMembers(collectionUri, vocabulary, searchString, pageRequest)
                .map(this::resolveEntity);
    }

    @Transactional
    public void addMember(SkosCollection<?> collection, Vocabulary vocabulary, URI member) {
        Objects.requireNonNull(collection, "SKOS collection must not be null!");
        Objects.requireNonNull(vocabulary, "Vocabulary must not be null!");
        Objects.requireNonNull(member, "Member must not be null!");
        collection.addMember(member);
        update(collection, vocabulary);
    }

    @Transactional
    public void removeMember(SkosCollection<?> collection, Vocabulary vocabulary, URI member) {
        Objects.requireNonNull(collection, "SKOS collection must not be null!");
        Objects.requireNonNull(vocabulary, "Vocabulary must not be null!");
        Objects.requireNonNull(member, "Member must not be null!");
        collection.removeMember(member);
        update(collection, vocabulary);
    }

    @Transactional
    public void removeMemberFromAllCollections(URI member) {
        Objects.requireNonNull(member, "Member must not be null!");
        findCollectionsWithMember(member).forEach(collection -> {
            collection.removeMember(member);
            // persisted by transaction commit
        });
    }

    @Transactional(readOnly = true)
    public Set<SkosCollection<?>> findCollectionsWithMember(URI member) {
        Objects.requireNonNull(member, "Member must not be null!");
        return collectionDao.findCollectionsWithMember(member).stream()
                .map(this::mapToCollection)
                .collect(Collectors.toSet());
    }

    private SkosCollection<?> mapToCollection(SkosCollectionDto entity) {
        Vocabulary vocabulary = new Vocabulary(entity.getVocabulary());
        if (entity.isOrdered()) {
            return findOrdered(entity.getUri(), vocabulary).orElse(null);
        }
        return findUnordered(entity.getUri(), vocabulary).orElse(null);
    }

    private static NotFoundException notFound(URI collectionUri, URI vocabularyUri) {
        return new NotFoundException("Collection " + Utils.uriToString(collectionUri) + " not found in vocabulary "
                + Utils.uriToString(vocabularyUri));
    }
}
