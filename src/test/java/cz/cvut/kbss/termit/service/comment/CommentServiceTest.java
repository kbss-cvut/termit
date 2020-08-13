package cz.cvut.kbss.termit.service.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.CommentReaction;
import cz.cvut.kbss.termit.model.comment.Dislike;
import cz.cvut.kbss.termit.model.comment.Like;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CommentServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private Configuration config;

    @Autowired
    private CommentService sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void addToAssetSetsAssetAndUserOfSpecifiedCommentAndPersistsIt() {
        final Term asset = Generator.generateTermWithId();
        final Comment comment = new Comment();
        comment.setContent("test");
        sut.addToAsset(comment, asset);

        final Comment result = em.find(Comment.class, comment.getUri());
        assertNotNull(result);
        assertEquals(author, result.getAuthor());
        assertEquals(asset.getUri(), result.getAsset());
    }

    @Test
    void findAllByAssetRetrievesCommentsForSpecifiedAsset() {
        final Term asset = Generator.generateTermWithId();
        final List<Comment> comments = IntStream.range(0, 5).mapToObj(i -> {
            final Comment c = new Comment();
            c.setContent("test " + i);
            c.setAsset(asset.getUri());
            c.setAuthor(author);
            return c;
        }).collect(Collectors.toList());
        transactional(() -> {
            final EntityDescriptor descriptor = new EntityDescriptor(
                    URI.create(config.get(ConfigParam.COMMENTS_CONTEXT)));
            descriptor.addAttributeDescriptor(Comment.getAuthorField(), new EntityDescriptor(null));
            comments.forEach(c -> em.persist(c, descriptor));
        });

        final List<Comment> result = sut.findAll(asset);
        assertEquals(comments.size(), result.size());
        assertTrue(comments.containsAll(result));
    }

    @Test
    void updateUpdatesSpecifiedComment() {
        final Comment comment = persistComment();

        final String newContent = "new content";
        comment.setContent(newContent);

        sut.update(comment);
        final Comment result = em.find(Comment.class, comment.getUri());
        assertNotNull(result);
        assertEquals(newContent, result.getContent());
    }

    private Comment persistComment() {
        final Term asset = Generator.generateTermWithId();
        final Comment comment = new Comment();
        comment.setContent("test ");
        comment.setAsset(asset.getUri());
        comment.setAuthor(author);
        transactional(() -> {
            final EntityDescriptor descriptor = new EntityDescriptor(
                    URI.create(config.get(ConfigParam.COMMENTS_CONTEXT)));
            descriptor.addAttributeDescriptor(Comment.getAuthorField(), new EntityDescriptor(null));
            em.persist(comment, descriptor);
        });
        return comment;
    }

    @Test
    void updateThrowsUnsupportedOperationExceptionWhenAttemptingToChangeCommentAsset() {
        final Comment comment = persistComment();
        comment.setAsset(Generator.generateUri());

        assertThrows(UnsupportedOperationException.class, () -> sut.update(comment));
    }

    @Test
    void updateThrowsUnsupportedOperationExceptionWhenAttemptingToChangeCommentAuthor() {
        final Comment comment = persistComment();
        comment.setAuthor(Generator.generateUserWithId());

        assertThrows(UnsupportedOperationException.class, () -> sut.update(comment));
    }

    @Test
    void updateThrowsUnsupportedOperationExceptionWhenAttemptingToChangeCommentCreationDate() {
        final Comment comment = persistComment();
        comment.setCreated(new Date(System.currentTimeMillis() - 100000));

        assertThrows(UnsupportedOperationException.class, () -> sut.update(comment));
    }

    @Test
    void updateThrowsAuthorizationExceptionWhenAttemptingToUpdateCommentBySomeoneElse() {
        final Comment comment = new Comment();
        comment.setContent("test ");
        comment.setAsset(Generator.generateUri());
        final User differentUser = Generator.generateUserWithId();
        comment.setAuthor(differentUser);
        transactional(() -> {
            em.persist(differentUser);
            final EntityDescriptor descriptor = new EntityDescriptor(
                    URI.create(config.get(ConfigParam.COMMENTS_CONTEXT)));
            descriptor.addAttributeDescriptor(Comment.getAuthorField(), new EntityDescriptor(null));
            em.persist(comment, descriptor);
        });

        final String newContent = "new content";
        comment.setContent(newContent);
        assertThrows(AuthorizationException.class, () -> sut.update(comment));
    }

    @Test
    void findRequiredRetrievesCommentFromRepositoryService() {
        final Comment comment = persistComment();

        final Comment result = sut.findRequired(comment.getUri());
        assertNotNull(result);
        assertEquals(comment, result);
    }

    @Test
    void findRequiredThrowsNotFoundExceptionWhenCommentCannotBeFound() {
        assertThrows(NotFoundException.class, () -> sut.findRequired(Generator.generateUri()));
    }

    @Test
    void removeRemovesSpecifiedComment() {
        final Comment comment = persistComment();

        sut.remove(comment);
        assertNull(em.find(Comment.class, comment.getUri()));
    }

    @Test
    void likeCommentCreatesLikeByCurrentUser() {
        final Comment comment = persistComment();

        sut.likeComment(comment);
        assertTrue(doesReactionExist(comment, em.getMetamodel().entity(Like.class).getIRI().toURI()));
    }

    private boolean doesReactionExist(Comment comment, URI type) {
        // TODO replace literal IRIs with constants once the comments model is settled
        return em.createNativeQuery("ASK WHERE {" +
                "?x a ?type ;" +
                "?hasAuthor ?author ;" +
                "?reactsTo ?comment .}", Boolean.class)
                 .setParameter("type", type)
                 .setParameter("hasAuthor", URI.create("https://www.w3.org/ns/activitystreams#actor"))
                 .setParameter("author", author)
                 .setParameter("reactsTo", URI.create("https://www.w3.org/ns/activitystreams#object"))
                 .setParameter("comment", comment).getSingleResult();
    }

    @Test
    void dislikeCommentCreatesDislikeByCurrentUser() {
        final Comment comment = persistComment();

        sut.dislikeComment(comment);
        assertTrue(doesReactionExist(comment, em.getMetamodel().entity(Dislike.class).getIRI().toURI()));
    }

    @Test
    void likeCommentRemovesPreexistingCommentReaction() {
        final Comment comment = persistComment();
        final CommentReaction existingReaction = persistReaction(comment);

        sut.likeComment(comment);
        assertNull(em.find(existingReaction.getClass(), existingReaction.getUri()));
        assertTrue(doesReactionExist(comment, em.getMetamodel().entity(Like.class).getIRI().toURI()));
    }

    private CommentReaction persistReaction(Comment toComment) {
        final CommentReaction reaction =
                Generator.randomBoolean() ? new Like(author, toComment) : new Dislike(author, toComment);
        transactional(
                () -> em.persist(reaction, new EntityDescriptor(URI.create(config.get(ConfigParam.COMMENTS_CONTEXT)))));
        return reaction;
    }

    @Test
    void dislikeCommentRemovesPreexistingCommentReaction() {
        final Comment comment = persistComment();
        final CommentReaction existingReaction = persistReaction(comment);

        sut.dislikeComment(comment);
        assertNull(em.find(existingReaction.getClass(), existingReaction.getUri()));
        assertTrue(doesReactionExist(comment, em.getMetamodel().entity(Dislike.class).getIRI().toURI()));
    }

    @Test
    void removeMyReactionToRemovesPreexistingReactionToComment() {
        final Comment comment = persistComment();
        final CommentReaction existingReaction = persistReaction(comment);

        sut.removeMyReactionTo(comment);
        assertNull(em.find(existingReaction.getClass(), existingReaction.getUri()));
        assertFalse(doesReactionExist(comment, em.getMetamodel().entity(existingReaction.getClass()).getIRI().toURI()));
    }
}
