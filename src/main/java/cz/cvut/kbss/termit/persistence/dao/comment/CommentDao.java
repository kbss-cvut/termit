package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.descriptors.FieldDescriptor;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CommentDao {

    private final Descriptor loadingDescriptor;
    private final Descriptor savingDescriptor;

    private final EntityManager em;

    @Autowired
    public CommentDao(EntityManager em, DescriptorFactory descriptorFactory, Configuration config) {
        this.em = em;
        this.loadingDescriptor = createLoadingDescriptor(config.getComments().getContext(), descriptorFactory);
        this.savingDescriptor = createSavingDescriptor(config.getComments().getContext(), descriptorFactory);
    }

    private Descriptor createLoadingDescriptor(String context, DescriptorFactory descriptorFactory) {
        final EntityDescriptor descriptor = new EntityDescriptor(URI.create(context), false);
        descriptor.addAttributeContext(descriptorFactory.fieldSpec(Comment.class, "author"), null);
        // Reaction are inferred, therefore possibly in the 'implicit' context (GraphDB)
        descriptor.addAttributeContext(descriptorFactory.fieldSpec(Comment.class, "reactions"), null);
        return descriptor;
    }

    private Descriptor createSavingDescriptor(String context, DescriptorFactory descriptorFactory) {
        final EntityDescriptor descriptor = new EntityDescriptor(URI.create(context));
        descriptor.addAttributeContext(descriptorFactory.fieldSpec(Comment.class, "author"), null);
        // Reaction are inferred, therefore possibly in the 'implicit' context (GraphDB)
        descriptor.addAttributeDescriptor(descriptorFactory.fieldSpec(Comment.class, "reactions"),
                new FieldDescriptor((URI) null, descriptorFactory.fieldSpec(Comment.class, "reactions")));
        return descriptor;
    }

    /**
     * Finds all comments related to the specified asset.
     *
     * @param asset Asset whose comments to retrieve
     * @return List of comments, sorted by date of creation (from oldest to newest)
     */
    public List<Comment> findAll(Asset<?> asset) {
        Objects.requireNonNull(asset);
        try {
            return em.createQuery("SELECT c FROM Comment c WHERE c.asset = :asset ORDER BY c.created", Comment.class)
                    .setParameter("asset", asset.getUri())
                    .setDescriptor(loadingDescriptor).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Finds comment with the specified identifier.
     *
     * @param id Comment identifier
     * @return Matching comment
     */
    public Optional<Comment> find(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.ofNullable(em.find(Comment.class, id, loadingDescriptor));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Persists the specified comment.
     *
     * @param comment Comment to persist
     */
    public void persist(Comment comment) {
        Objects.requireNonNull(comment);
        try {
            em.persist(comment, savingDescriptor);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Updates the specified comment.
     *
     * @param comment Comment update
     */
    public void update(Comment comment) {
        Objects.requireNonNull(comment);
        try {
            em.merge(comment, savingDescriptor);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Deletes the specified comment.
     *
     * @param comment Comment to delete
     */
    public void remove(Comment comment) {
        Objects.requireNonNull(comment);
        try {
            em.remove(em.getReference(Comment.class, comment.getUri()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private List<Comment> findUniqueLastModifiedEntitiesBy(User author, int limit) {
        return em.createNativeQuery(
                         "SELECT DISTINCT ?comment WHERE {"
                                 + "?comment a ?commentType ;"
                                 + "       ?hasEditor ?editor ;"
                                 + "       ?hasAsset ?asset ."
                                 + "    OPTIONAL { ?comment ?hasModificationDate ?modified . }"
                                 + "    OPTIONAL { ?comment ?hasCreationDate  ?created . }"
                                 + "    BIND(COALESCE(?modified,?created) AS ?lastModified)"
                                 + "    { FILTER( ?editor = ?author) } UNION {"
                                 + "       ?comment2 a ?commentType ;"
                                 + "       ?hasEditor ?author ;"
                                 + "       ?hasAsset ?asset ."
                                 + "       OPTIONAL { ?comment2 ?hasModificationDate ?modified2 . }"
                                 + "       OPTIONAL { ?comment2 ?hasCreationDate ?created2 . }"
                                 + "       BIND(COALESCE(?modified2,?created2) AS ?lastModified2)"
                                 + "       FILTER( ?lastModified2 < ?lastModified )"
                                 + "     }"
                                 + "    "
                                 + "} ORDER BY DESC(?lastModified)", Comment.class)
                 .setParameter("commentType", URI.create(Vocabulary.s_c_Comment))
                 .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace))
                 .setParameter("hasCreationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_vytvoreni))
                 .setParameter("hasEditor", URI.create(Vocabulary.s_p_has_creator))
                 .setParameter("author", author.getUri())
                 .setParameter("hasAsset", URI.create(Vocabulary.s_p_topic))
                 .setMaxResults(limit)
                 .getResultList();
    }

    /**
     * Finds the specified number of most recently added/edited comments by the specified author
     * and reactions on them.
     *
     * @param author Author of the modifications
     * @param limit  Number of assets to load
     * @return List of assets recently added/edited by the specified user
     */
    public List<Comment> findLastEditedBy(User author, int limit) {
        Objects.requireNonNull(author);
        try {
            return findUniqueLastModifiedEntitiesBy(author, limit);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
