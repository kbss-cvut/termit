package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.CommentReaction;
import cz.cvut.kbss.termit.model.comment.Like;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class CommentReactionDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private Configuration config;

    @Autowired
    private CommentReactionDao sut;

    private Descriptor descriptor;

    @BeforeEach
    void setUp() {
        this.descriptor = new EntityDescriptor(URI.create(config.get(ConfigParam.COMMENTS_CONTEXT)));
    }

    @Test
    void persistPersistsSpecifiedReactionIntoCommentContext() {
        final CommentReaction reaction = new Like();
        reaction.setActor(Generator.generateUserWithId().getUri());
        reaction.setObject(Generator.generateUri());
        transactional(() -> sut.persist(reaction));

        final CommentReaction result = em.find(Like.class, reaction.getUri(), descriptor);
        assertNotNull(result);
        assertEquals(reaction.getActor(), result.getActor());
        assertEquals(reaction.getObject(), result.getObject());
    }

    @Test
    void removeExistingDeletesExistingReactionBySpecifiedAuthorToSpecifiedComment() {
        // No need to persist either, we just need their IDs
        final User user = Generator.generateUserWithId();
        final Comment comment = new Comment();
        comment.setUri(Generator.generateUri());
        final CommentReaction reaction = new Like();
        reaction.setActor(user.getUri());
        reaction.setObject(comment.getUri());
        transactional(() -> em.persist(reaction, descriptor));

        assertNotNull(em.find(Like.class, reaction.getUri(), descriptor));
        transactional(() -> sut.removeExisting(user, comment));
        assertNull(em.find(Like.class, reaction.getUri(), descriptor));
    }

    @Test
    void removeDoesNothingWhenNoPreexistingReactionMatchesSpecifiedParameters() {
        // No need to persist either, we just need their IDs
        final User user = Generator.generateUserWithId();
        final Comment comment = new Comment();
        comment.setUri(Generator.generateUri());
        final CommentReaction differentReaction = new Like();
        differentReaction.setActor(user.getUri());
        differentReaction.setObject(comment.getUri());
        transactional(() -> em.persist(differentReaction, descriptor));

        final Comment anotherComment = new Comment();
        anotherComment.setUri(Generator.generateUri());
        assertNotNull(em.find(Like.class, differentReaction.getUri(), descriptor));
        transactional(() -> sut.removeExisting(user, anotherComment));
        assertNotNull(em.find(Like.class, differentReaction.getUri(), descriptor));
    }
}
