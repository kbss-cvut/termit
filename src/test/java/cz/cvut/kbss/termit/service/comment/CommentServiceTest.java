package cz.cvut.kbss.termit.service.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
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
        sut.addToAsset(asset, comment);

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
}
