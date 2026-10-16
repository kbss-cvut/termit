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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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
     * Loads members of the collection and maps them to respective entity instances.
     *
     * <p>Possible entity objects are:
     *
     * <ul>
     *   <li>{@link cz.cvut.kbss.termit.dto.TermInfo}
     *   <li>{@link cz.cvut.kbss.termit.dto.SkosCollectionDto}
     * </ul>
     *
     * @param collection The collection whose members should be loaded
     * @param pageable page specification
     * @return list of entities that are part of the collection
     */
    @Transactional
    public List<HasIdentifier> findMembers(SkosCollection<?> collection, Pageable pageable) {
        final Map<URI, HasIdentifier> resolvedEntities =
                new HashMap<>(Math.min(collection.getMembers().size(), pageable.getPageSize()));
        try (Stream<TypeAwareEntity> stream = collectionDao.findMembers(collection, pageable)) {
            stream.map(this::resolveEntity).forEach(entity -> {
                Objects.requireNonNull(entity);
                resolvedEntities.put(entity.getUri(), entity);
            });
        }

        return collection.getMembers().stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .map(resolvedEntities::get)
                .toList();
    }

    /**
     * Finds SKOS collection by its identifier in the given vocabulary.
     *
     * @param collectionUri the identifier of the collection
     * @param vocabulary the vocabulary to which the collection belongs
     * @return the resolved {@link TermCollection} or {@link OrderedTermCollection}
     */
    @Transactional
    public Optional<SkosCollection<?>> find(URI collectionUri, Vocabulary vocabulary) {
        return Optional.ofNullable(collectionDao.findCollectionType(collectionUri))
                .map(type -> collectionDao.find(type, collectionUri, vocabulary));
    }

    @Transactional
    public Optional<TermCollection> findUnordered(URI collectionUri, Vocabulary vocabulary) {
        return Optional.ofNullable(collectionDao.find(TermCollection.class, collectionUri, vocabulary));
    }

    @Transactional
    public Optional<OrderedTermCollection> findOrdered(URI collectionUri, Vocabulary vocabulary) {
        return Optional.ofNullable(collectionDao.find(OrderedTermCollection.class, collectionUri, vocabulary));
    }

    /**
     * Loads the respective entity object depending on the type of the entity.
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
