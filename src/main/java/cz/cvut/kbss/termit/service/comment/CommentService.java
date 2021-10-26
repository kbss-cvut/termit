package cz.cvut.kbss.termit.service.comment;

import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.CommentReaction;
import cz.cvut.kbss.termit.persistence.dao.comment.CommentDao;
import cz.cvut.kbss.termit.persistence.dao.comment.CommentReactionDao;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Service
public class CommentService {

    private final SecurityUtils securityUtils;

    private final CommentDao dao;

    private final CommentReactionDao reactionDao;

    @Autowired
    public CommentService(SecurityUtils securityUtils, CommentDao dao, CommentReactionDao reactionDao) {
        this.securityUtils = securityUtils;
        this.dao = dao;
        this.reactionDao = reactionDao;
    }

    /**
     * Gets all comments for the specified asset.
     *
     * @param asset Target of the comments
     * @return List of comments, ordered by date of creation
     */
    public List<Comment> findAll(Asset<?> asset) {
        return dao.findAll(asset);
    }

    /**
     * Finds a comment with the specified identifier.
     *
     * @param id Comment identifier
     * @return Matching comment
     * @throws NotFoundException If comment with the specified identifier does not exist
     */
    public Comment findRequired(URI id) {
        Objects.requireNonNull(id);
        return dao.find(id).orElseThrow(() -> NotFoundException.create(Comment.class.getSimpleName(), id));
    }

    /**
     * Adds the specified comment to the specified asset.
     *
     * @param comment Comment to save
     * @param asset   Target of the comment
     */
    @Transactional
    public void addToAsset(Comment comment, Asset<?> asset) {
        Objects.requireNonNull(asset);
        Objects.requireNonNull(comment);
        comment.setAuthor(currentUser());
        comment.setAsset(asset.getUri());
        dao.persist(comment);
    }

    private User currentUser() {
        return securityUtils.getCurrentUser().toUser();
    }

    /**
     * Updates the specified comment.
     *
     * @param comment Comment to update
     */
    @Transactional
    public void update(Comment comment) {
        Objects.requireNonNull(comment);
        final Comment existing = dao.find(comment.getUri()).orElseThrow(
                () -> NotFoundException.create(Comment.class.getSimpleName(), comment.getUri()));
        if (!Objects.equals(existing.getAuthor(), currentUser())) {
            throw new AuthorizationException("Cannot update someone else's comment.");
        }
        if (!Objects.equals(existing.getAsset(), comment.getAsset()) ||
                !Objects.equals(existing.getAuthor(), comment.getAuthor()) ||
                !Objects.equals(existing.getCreated(), comment.getCreated())) {
            throw new UnsupportedOperationException(
                    "Cannot modify commented asset, author or date of creation of a comment!");
        }
        dao.update(comment);
    }

    /**
     * Removes the specified comment.
     *
     * @param comment Comment to remove
     */
    @Transactional
    public void remove(Comment comment) {
        Objects.requireNonNull(comment);
        dao.remove(comment);
    }

    /**
     * Creates a reaction by the current user to the specified comment.
     * <p>
     * Note that at most one reaction can exist by one user to a comment, so this method removes any preexisting
     * reactions.
     *
     * @param comment Comment being liked
     * @param type    Type of the reaction
     */
    @Transactional
    public void addReactionTo(Comment comment, String type) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(type);
        removeMyReactionTo(comment);
        final CommentReaction reaction = new CommentReaction(currentUser(), comment);
        reaction.addType(type);
        reactionDao.persist(reaction);
    }

    /**
     * Removes the current user's reaction to the specified comment.
     * <p>
     * If no reaction by the current user to the specified comment exists, nothing happens.
     *
     * @param comment Comment to which reaction will be removed
     */
    @Transactional
    public void removeMyReactionTo(Comment comment) {
        Objects.requireNonNull(comment);
        reactionDao.removeExisting(currentUser(), comment);
    }

    /**
     * Finds the specified number of the current user's most recently added/edited comments and reactions on them.
     *
     * @param limit Maximum number of comments to retrieve
     * @return List of recently added/edited comments
     */
    public List<Comment> findLastEditedByMe(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Maximum for recently edited comments must not be less than 0.");
        }
        final User me = securityUtils.getCurrentUser().toUser();
        return dao.findLastEditedBy(me, limit);
    }
}
