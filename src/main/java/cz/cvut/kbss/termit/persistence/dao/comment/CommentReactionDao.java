package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.CommentReaction;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Objects;

@Repository
public class CommentReactionDao {

    private final EntityManager em;

    // Comments are stored in their own context, together with reactions
    private final Descriptor commentDescriptor;

    @Autowired
    public CommentReactionDao(EntityManager em, Configuration config) {
        this.em = em;
        this.commentDescriptor = new EntityDescriptor(URI.create(config.get(ConfigParam.COMMENTS_CONTEXT)));
    }

    /**
     * Persists the specified comment reaction.
     *
     * @param reaction React
     */
    public void persist(CommentReaction reaction) {
        Objects.requireNonNull(reaction);
        try {
            em.persist(reaction, commentDescriptor);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Removes existing reaction by the specified author to the specified comment.
     *
     * @param author  Author of the reaction
     * @param comment Comment reacted to
     */
    public void removeExisting(User author, Comment comment) {
        try {
            em.createNativeQuery("DELETE WHERE {" +
                    "?x a ?type ;" +
                    "?hasAuthor ?author ;" +
                    "?reactsTo ?comment . }")
                    .setParameter("hasAuthor", em.getMetamodel().entity(CommentReaction.class).getAttribute("actor").getIRI().toURI())
                    .setParameter("author", author)
                    .setParameter("reactsTo", em.getMetamodel().entity(CommentReaction.class).getAttribute("object").getIRI().toURI())
                    .setParameter("comment", comment).executeUpdate();
            em.getEntityManagerFactory().getCache()
                    .evict(Comment.class, comment.getUri(), commentDescriptor.getSingleContext().orElse(null));
            em.getEntityManagerFactory().getCache().evict(CommentReaction.class);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
