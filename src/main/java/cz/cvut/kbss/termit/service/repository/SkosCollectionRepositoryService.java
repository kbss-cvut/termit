package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.TypeAwareEntity;
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

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class SkosCollectionRepositoryService {
    private final SkosCollectionDao collectionDao;
    private final TermRepositoryService termRepositoryService;

    public SkosCollectionRepositoryService(
            SkosCollectionDao collectionDao, TermRepositoryService termRepositoryService) {
        this.collectionDao = collectionDao;
        this.termRepositoryService = termRepositoryService;
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
        final List<HasIdentifier> data = resolveEntities(collectionDao.findMembers(collection, pageable));
        return new PageImpl<>(data, pageable, collection.getMembers().size());
    }

    /**
     * Loads members of the collection sorted by their label and maps them to respective entity instances.
     *
     * <p>Members with a label in the specified language are prioritized.
     *
     * @param collection The collection whose members should be loaded
     * @param pageable page specification, the sort direction is taken from the {@code label} property
     * @param language language of the labels used for sorting
     * @return page of entities that are part of the collection
     * @see #resolveEntity(TypeAwareEntity)
     */
    @Transactional(readOnly = true)
    public Page<HasIdentifier> findMembers(TermCollection collection, Pageable pageable, String language) {
        final List<HasIdentifier> data = resolveEntities(collectionDao.findMembers(collection, pageable, language));
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
     * @return the resolved {@link TermCollection} or {@link OrderedTermCollection}
     */
    @Transactional(readOnly = true)
    public Optional<SkosCollection<?>> find(URI collectionUri, Vocabulary vocabulary) {
        return Optional.ofNullable(collectionDao.findCollectionType(collectionUri))
                .map(type -> collectionDao.find(type, collectionUri, vocabulary));
    }

    @Transactional(readOnly = true)
    public Optional<TermCollection> findUnordered(URI collectionUri, Vocabulary vocabulary) {
        return Optional.ofNullable(collectionDao.find(TermCollection.class, collectionUri, vocabulary));
    }

    @Transactional(readOnly = true)
    public Optional<OrderedTermCollection> findOrdered(URI collectionUri, Vocabulary vocabulary) {
        return Optional.ofNullable(collectionDao.find(OrderedTermCollection.class, collectionUri, vocabulary));
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
            return collectionDao.findDto(entity.getUri());
        }
        throw new ValidationException("Illegal entity in SKOS collection: " + Utils.uriToString(entity.getUri()));
    }
}
