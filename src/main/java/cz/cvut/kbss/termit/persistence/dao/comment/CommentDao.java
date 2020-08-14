package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CommentDao {

    private final Descriptor commentDescriptor;

    private final EntityManager em;

    @Autowired
    public CommentDao(EntityManager em, Configuration config) {
        this.em = em;
        this.commentDescriptor = createDescriptor(config.get(ConfigParam.COMMENTS_CONTEXT));
    }

    private Descriptor createDescriptor(String context) {
        final EntityDescriptor descriptor = new EntityDescriptor(URI.create(context));
        descriptor.addAttributeDescriptor(Comment.getAuthorField(), new EntityDescriptor(null));
        // Reaction are inferred, therefore possibly in the 'implicit' context (GraphDB)
        descriptor.addAttributeDescriptor(Comment.getReactionsField(), new EntityDescriptor(null));
        return descriptor;
    }

    /**
     * Finds all comments related to the specified asset.
     *
     * @param asset Asset whose comments to retrieve
     * @return List of comments, sorted by date of creation (from oldest to newest)
     */
    public List<Comment> findAll(Asset asset) {
        Objects.requireNonNull(asset);
        try {
            return em.createQuery("SELECT c FROM Comment c WHERE c.asset = :asset ORDER BY c.created", Comment.class)
                     .setParameter("asset", asset.getUri())
                     .setDescriptor(commentDescriptor).getResultList();
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
            return Optional.ofNullable(em.find(Comment.class, id, commentDescriptor));
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
            em.persist(comment, commentDescriptor);
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
            em.merge(comment, commentDescriptor);
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
}
