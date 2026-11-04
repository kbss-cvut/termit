package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection_;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection_;
import cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper;

import java.net.URI;

/** Indicates that the type of {@link cz.cvut.kbss.termit.model.collection.SkosCollection} is about to change. */
public class CollectionTypeUpdateEvent extends AssetUpdateEvent {
    private final URI originalType;
    private final URI newType;

    public CollectionTypeUpdateEvent(Object source, SkosCollection<?> collection, URI originalType, URI newType) {
        super(source, collection);
        this.originalType = originalType;
        this.newType = newType;
    }

    /**
     * Constructs event from the collection entity before its type changed.
     *
     * @param source the source of the event
     * @param collectionBeforeTypeChange the original collection before conversion to the other type
     */
    public CollectionTypeUpdateEvent(Object source, SkosCollection<?> collectionBeforeTypeChange) {
        this(
                source,
                collectionBeforeTypeChange,
                resolveOriginalType(collectionBeforeTypeChange),
                resolveNewType(collectionBeforeTypeChange));
    }

    private static URI resolveOriginalType(SkosCollection<?> collectionBeforeTypeChange) {
        return URI.create(EntityToOwlClassMapper.getOwlClassForEntity(collectionBeforeTypeChange.getClass()));
    }

    /**
     * If the collection is currently ordered, returns unordered collection type. If the collection is not currently
     * ordered, returns the ordered collection type.
     */
    private static URI resolveNewType(SkosCollection<?> collectionBeforeTypeChange) {
        if (collectionBeforeTypeChange instanceof OrderedTermCollection) {
            return TermCollection_.entityClassIRI.toURI();
        }
        return OrderedTermCollection_.entityClassIRI.toURI();
    }

    public URI getOriginalType() {
        return originalType;
    }

    public URI getNewType() {
        return newType;
    }

    public SkosCollection<?> getCollectionBeforeTypeChange() {
        return (SkosCollection<?>) getAsset();
    }
}
