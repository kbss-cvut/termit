/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.CommentReaction;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        this.descriptor = new EntityDescriptor(URI.create(config.getComments().getContext()));
    }

    @Test
    void persistPersistsSpecifiedReactionIntoCommentContext() {
        final Comment c = new Comment();
        c.setUri(Generator.generateUri());
        final CommentReaction reaction = new CommentReaction(Generator.generateUserWithId(), c);
        final String type = "https://www.w3.org/ns/activitystreams#Like";
        reaction.addType(type);
        transactional(() -> sut.persist(reaction));

        final CommentReaction result = em.find(CommentReaction.class, reaction.getUri(), descriptor);
        assertNotNull(result);
        assertEquals(reaction.getActor(), result.getActor());
        assertEquals(reaction.getObject(), result.getObject());
        assertThat(reaction.getTypes(), hasItem(type));
    }

    @Test
    void removeExistingDeletesExistingReactionBySpecifiedAuthorToSpecifiedComment() {
        // No need to persist either, we just need their IDs
        final User user = Generator.generateUserWithId();
        final Comment comment = new Comment();
        comment.setUri(Generator.generateUri());
        final CommentReaction reaction = new CommentReaction(user, comment);
        reaction.setActor(user.getUri());
        reaction.setObject(comment.getUri());
        reaction.addType("https://www.w3.org/ns/activitystreams#Like");
        transactional(() -> em.persist(reaction, descriptor));

        assertNotNull(em.find(CommentReaction.class, reaction.getUri(), descriptor));
        transactional(() -> sut.removeExisting(user, comment));
        assertNull(em.find(CommentReaction.class, reaction.getUri(), descriptor));
    }

    @Test
    void removeDoesNothingWhenNoPreexistingReactionMatchesSpecifiedParameters() {
        // No need to persist either, we just need their IDs
        final User user = Generator.generateUserWithId();
        final Comment comment = new Comment();
        comment.setUri(Generator.generateUri());
        final CommentReaction differentReaction = new CommentReaction(user, comment);
        differentReaction.setActor(user.getUri());
        differentReaction.setObject(comment.getUri());
        differentReaction.addType("https://www.w3.org/ns/activitystreams#Like");
        transactional(() -> em.persist(differentReaction, descriptor));

        final Comment anotherComment = new Comment();
        anotherComment.setUri(Generator.generateUri());
        assertNotNull(em.find(CommentReaction.class, differentReaction.getUri(), descriptor));
        transactional(() -> sut.removeExisting(user, anotherComment));
        assertNotNull(em.find(CommentReaction.class, differentReaction.getUri(), descriptor));
    }
}
