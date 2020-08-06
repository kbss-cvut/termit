package cz.cvut.kbss.termit.service.comment;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.persistence.dao.comment.CommentDao;
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

    @Autowired
    public CommentService(SecurityUtils securityUtils, CommentDao dao) {
        this.securityUtils = securityUtils;
        this.dao = dao;
    }

    /**
     * Gets all comments for the specified asset.
     *
     * @param asset Target of the comments
     * @return List of comments, ordered by date of creation
     */
    public List<Comment> findAll(Asset asset) {
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
    public void addToAsset(Comment comment, Asset asset) {
        Objects.requireNonNull(asset);
        Objects.requireNonNull(comment);
        comment.setAuthor(securityUtils.getCurrentUser().toUser());
        comment.setAsset(asset.getUri());
        dao.persist(comment);
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
}
