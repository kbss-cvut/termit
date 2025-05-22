/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
import cz.cvut.kbss.termit.model.comment.Comment_;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        }).toList();
        transactional(() -> {
            final EntityDescriptor descriptor = new EntityDescriptor(URI.create(config.getComments().getContext()));
            descriptor.addAttributeContext(Comment_.author, null);
            comments.forEach(c -> {
                em.persist(c, descriptor);
                c.setCreated(Utils.timestamp().minusSeconds(5));
            });
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
            final EntityDescriptor descriptor = new EntityDescriptor(URI.create(config.getComments().getContext()));
            descriptor.addAttributeContext(Comment_.author, null);
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
        comment.setCreated(Instant.ofEpochMilli(System.currentTimeMillis() - 100000));

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
            final EntityDescriptor descriptor = new EntityDescriptor(URI.create(config.getComments().getContext()));
            descriptor.addAttributeContext(Comment_.author, null);
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
    void addReactionToCreatesLikeByCurrentUser() {
        final Comment comment = persistComment();
        final URI reaction = URI.create(Vocabulary.s_c_as_Like);

        sut.addReactionTo(comment, reaction.toString());
        assertTrue(doesReactionExist(comment, reaction));
    }

    private boolean doesReactionExist(Comment comment, URI type) {
        return em.createNativeQuery("ASK WHERE {" +
                "?x a ?type ;" +
                "?hasAuthor ?author ;" +
                "?reactsTo ?comment .}", Boolean.class)
                .setParameter("type", type)
                .setParameter("hasAuthor", URI.create(Vocabulary.s_p_as_actor))
                .setParameter("author", author)
                .setParameter("reactsTo", URI.create(Vocabulary.s_p_as_object))
                .setParameter("comment", comment).getSingleResult();
    }

    @Test
    void addReactionToRemovesPreexistingCommentReaction() {
        final Comment comment = persistComment();
        final CommentReaction existingReaction = persistReaction(comment);
        final URI reaction = URI.create(Vocabulary.s_c_as_Like);

        sut.addReactionTo(comment, reaction.toString());
        assertNull(em.find(CommentReaction.class, existingReaction.getUri()));
        assertTrue(doesReactionExist(comment, reaction));
    }

    private CommentReaction persistReaction(Comment toComment) {
        final CommentReaction reaction = new CommentReaction(author, toComment);
        reaction.addType(Vocabulary.s_c_as_Dislike);
        transactional(
                () -> em.persist(reaction, new EntityDescriptor(URI.create(config.getComments().getContext()))));
        return reaction;
    }

    @Test
    void removeMyReactionToRemovesPreexistingReactionToComment() {
        final Comment comment = persistComment();
        final CommentReaction existingReaction = persistReaction(comment);

        sut.removeMyReactionTo(comment);
        assertNull(em.find(CommentReaction.class, existingReaction.getUri()));
        assertFalse(doesReactionExist(comment, URI.create(existingReaction.getTypes().iterator().next())));
    }
}
