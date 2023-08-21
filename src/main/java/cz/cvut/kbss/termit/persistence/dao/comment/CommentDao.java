package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.descriptors.FieldDescriptor;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.Comment_;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class CommentDao {

    private final EntityManager em;
    private final String commentContext;

    @Autowired
    public CommentDao(EntityManager em, Configuration config) {
        this.em = em;
        this.commentContext = config.getComments().getContext();
    }

    private Descriptor createLoadingDescriptor() {
        final EntityDescriptor descriptor = new EntityDescriptor(URI.create(commentContext), false);
        descriptor.addAttributeContext(Comment_.author, null);
        // Reaction are inferred, therefore possibly in the 'implicit' context (GraphDB)
        descriptor.addAttributeContext(Comment_.reactions, null);
        return descriptor;
    }

    private Descriptor createSavingDescriptor() {
        final EntityDescriptor descriptor = new EntityDescriptor(URI.create(commentContext));
        descriptor.addAttributeContext(Comment_.author, null);
        // Reaction are inferred, therefore possibly in the 'implicit' context (GraphDB)
        descriptor.addAttributeDescriptor(Comment_.reactions, new FieldDescriptor((URI) null, Comment_.reactions));
        return descriptor;
    }

    /**
     * Finds all comments related to the specified asset.
     *
     * @param asset Asset whose comments to retrieve
     * @return List of matching comments, sorted by date of creation (from oldest to newest)
     */
    public List<Comment> findAll(Asset<?> asset) {
        return findAll(asset, Constants.EPOCH_TIMESTAMP, Utils.timestamp());
    }

    /**
     * Finds all comments related to the specified asset created or edited in the specified time interval.
     * <p>
     * All the parameters are optional.
     *
     * @param asset Asset whose comments to retrieve, optional
     * @param from  Start timestamp of the time interval for comments retrieval. Optional, if not provided, Unix epoch
     *              is used
     * @param to    End timestamp of the time interval for comments retrieval. Optional, if not provided, current date
     *              and time is used
     * @return List of matching comments, sorted by date of creation (from oldest to newest)
     */
    public List<Comment> findAll(Asset<?> asset, Instant from, Instant to) {
        try {
            final TypedQuery<Comment> query = em.createNativeQuery("SELECT DISTINCT ?c WHERE {" +
                                                                           "?c a ?type ; " +
                                                                           "?hasTopic ?asset . " +
                                                                           " { " +
                                                                           "?c ?hasCreated ?mod . " +
                                                                           "FILTER (?mod >= ?from && ?mod < ?to) " +
                                                                           "} UNION { " +
                                                                           "?c ?hasModified ?mod . " +
                                                                           "FILTER (?mod >= ?from && ?mod < ?to) " +
                                                                           "} } ORDER BY ?mod", Comment.class)
                                                .setParameter("type", URI.create(Vocabulary.s_c_Comment))
                                                .setParameter("hasTopic", URI.create(Vocabulary.s_p_topic))
                                                .setParameter("hasCreated",
                                                              URI.create(Vocabulary.s_p_ma_datum_a_cas_vytvoreni))
                                                .setParameter("hasModified", URI.create(
                                                        Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace));
            if (asset != null) {
                query.setParameter("asset", asset);
            }
            return query.setParameter("from", from != null ? from : Constants.EPOCH_TIMESTAMP)
                        .setParameter("to", to != null ? to : Utils.timestamp())
                        .setDescriptor(createLoadingDescriptor()).getResultList();
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
            return Optional.ofNullable(em.find(Comment.class, id, createLoadingDescriptor()));
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
            em.persist(comment, createSavingDescriptor());
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
            em.merge(comment, createSavingDescriptor());
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
            find(comment.getUri()).ifPresent(em::remove);
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
     * Finds the specified number of most recently added/edited comments by the specified author and reactions on them.
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
