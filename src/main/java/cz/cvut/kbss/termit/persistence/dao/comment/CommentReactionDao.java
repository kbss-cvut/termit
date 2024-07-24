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
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.CommentReaction;
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
        this.commentDescriptor = new EntityDescriptor(URI.create(config.getComments().getContext()));
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
