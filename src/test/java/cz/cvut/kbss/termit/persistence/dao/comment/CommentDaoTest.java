package cz.cvut.kbss.termit.persistence.dao.comment;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.descriptors.FieldDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.comment.CommentReaction;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CommentDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private Configuration configuration;

    @Autowired
    private CommentDao sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
    }

    @Test
    void persistGeneratesDateOfCreation() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> sut.persist(comment));

        final Comment result = em.find(Comment.class, comment.getUri());
        assertNotNull(result);
        assertNotNull(result.getCreated());
    }

    @Test
    void persistSavesSpecifiedCommentIntoCommentContext() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> sut.persist(comment));

        final EntityDescriptor descriptor = createDescriptor();
        final Comment result = em.find(Comment.class, comment.getUri(), descriptor);
        assertNotNull(result);
    }

    private Comment generateComment(URI assetIri) {
        final Comment comment = new Comment();
        comment.setContent("Comment to an asset.");
        comment.setAuthor(author);
        comment.setAsset(assetIri);
        return comment;
    }

    private EntityDescriptor createDescriptor() {
        final EntityDescriptor descriptor = new EntityDescriptor(
                URI.create(configuration.getComments().getContext()));
        descriptor.addAttributeDescriptor(em.getMetamodel().entity(Comment.class).getAttribute("author"),
                                          new EntityDescriptor((URI) null));
        descriptor.addAttributeDescriptor(em.getMetamodel().entity(Comment.class).getAttribute("reactions"),
                                          new FieldDescriptor((URI) null, em.getMetamodel().entity(Comment.class)
                                                                            .getAttribute("reactions")));
        return descriptor;
    }

    @Test
    void updateSetsLastModifiedValue() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(comment, createDescriptor()));

        comment.setContent("Updated content.");
        transactional(() -> sut.update(comment));

        em.getEntityManagerFactory().getCache().evictAll();
        final Comment result = em.find(Comment.class, comment.getUri());
        assertNotNull(result);
        assertNotNull(result.getModified());
        assertNotEquals(result.getCreated(), result.getModified());
    }

    @Test
    void updateMakesChangesInCommentContext() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(comment, createDescriptor()));

        final String newContent = "Updated content.";
        comment.setContent(newContent);
        transactional(() -> sut.update(comment));

        em.getEntityManagerFactory().getCache().evictAll();
        final Comment result = em.find(Comment.class, comment.getUri(), createDescriptor());
        assertNotNull(result);
        assertEquals(newContent, result.getContent());
    }

    @Test
    void removeDeletesSpecifiedComment() {
        final Comment comment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(comment, createDescriptor()));

        transactional(() -> sut.remove(comment));

        assertNull(em.find(Comment.class, comment.getUri()));
    }

    @Test
    void findAllByAssetRetrievesAllCommentsForSpecifiedAsset() {
        final Term term = Generator.generateTermWithId();

        final List<Comment> comments = IntStream.range(0, 10).mapToObj(i -> generateComment(term.getUri())).collect(
                Collectors.toList());
        final EntityDescriptor descriptor = createDescriptor();
        transactional(() -> comments.forEach(c -> em.persist(c, descriptor)));
        final Comment anotherComment = generateComment(Generator.generateUri());
        transactional(() -> em.persist(anotherComment, descriptor));

        final List<Comment> result = sut.findAll(term);
        assertNotNull(result);
        assertEquals(comments.size(), result.size());
        assertTrue(comments.containsAll(result));
    }

    @Test
    void findAllByAssetHandlesCorrectlyReactionContext() {
        final Term term = Generator.generateTermWithId();

        final Comment comment = generateComment(term.getUri());
        final EntityDescriptor descriptor = createDescriptor();
        transactional(() -> {
            em.persist(comment, descriptor);
            final CommentReaction reaction = new CommentReaction(author, comment);
            reaction.addType(Vocabulary.s_c_Like);
            em.persist(reaction, new EntityDescriptor(URI.create(configuration.getComments().getContext())));
            generateCommentReactionReference(reaction);
        });
        em.getEntityManagerFactory().getCache().evictAll();
        final List<Comment> result = sut.findAll(term);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    private void generateCommentReactionReference(CommentReaction reaction) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.add(vf.createIRI(reaction.getObject().toString()),
                     vf.createIRI(Vocabulary.s_p_ma_reakci),
                     vf.createIRI(reaction.getUri().toString()));
        }
    }

    @Test
    void findAllByAssetAndTimeIntervalRetrievesCommentsCreatedInSpecifiedTimePeriod() {
        final Term term = Generator.generateTermWithId();

        final List<Comment> comments = IntStream.range(0, 10).mapToObj(i -> generateComment(term.getUri())).collect(
                Collectors.toList());
        final EntityDescriptor descriptor = createDescriptor();
        transactional(() -> comments.forEach(c -> em.persist(c, descriptor)));
        transactional(() -> {
            for (int i = 0; i < comments.size(); i++) {
                final Comment c = comments.get(i);
                c.setCreated(Utils.timestamp().minus(i, ChronoUnit.DAYS));
                em.merge(c, descriptor);
            }
        });
        final Instant from = Utils.timestamp().minus(comments.size() / 2, ChronoUnit.DAYS);
        final Instant to = Utils.timestamp().minus(1, ChronoUnit.DAYS);

        final List<Comment> result = sut.findAll(term, from, to);
        verifyCommentInterval(from, to, result);
    }

    private void verifyCommentInterval(Instant from, Instant to, List<Comment> result) {
        assertFalse(result.isEmpty());
        result.forEach(c -> assertThat(c, anyOf(
                hasProperty("created", both(greaterThanOrEqualTo(from)).and(lessThan(to))),
                hasProperty("modified", both(greaterThanOrEqualTo(from)).and(lessThan(to)))
        )));
    }

    @Test
    void removeCascadesRemovalToReactions() {
        final URI assetUri = Generator.generateUri();
        final Comment comment = generateComment(assetUri);
        final EntityDescriptor descriptor = createDescriptor();
        transactional(() -> {
            em.persist(comment, descriptor);
            final CommentReaction reaction = new CommentReaction(author, comment);
            reaction.addType(Vocabulary.s_c_Like);
            em.persist(reaction, new EntityDescriptor(URI.create(configuration.getComments().getContext())));
            generateCommentReactionReference(reaction);
        });

        transactional(() -> {
            final Optional<Comment> toRemove = sut.find(comment.getUri());
            assertTrue(toRemove.isPresent());
            sut.remove(toRemove.get());
        });

        assertFalse(em.createNativeQuery("ASK WHERE { ?x ?reactsTo ?comment }", Boolean.class)
                      .setParameter("reactsTo", URI.create(Vocabulary.s_p_object))
                      .setParameter("comment", comment).getSingleResult());
    }

    @Test
    void findAllByAssetAndTimeIntervalAllowsMissingAsset() {
        final List<Comment> comments = IntStream.range(0, 10).mapToObj(i -> generateComment(Generator.generateUri()))
                                                .collect(
                                                        Collectors.toList());
        final EntityDescriptor descriptor = createDescriptor();
        transactional(() -> comments.forEach(c -> em.persist(c, descriptor)));
        transactional(() -> {
            for (int i = 0; i < comments.size(); i++) {
                final Comment c = comments.get(i);
                c.setCreated(Utils.timestamp().minus(i, ChronoUnit.DAYS));
                em.merge(c, descriptor);
            }
        });
        final Instant from = Utils.timestamp().minus(comments.size() / 2, ChronoUnit.DAYS);
        final Instant to = Utils.timestamp().minus(1, ChronoUnit.DAYS);

        final List<Comment> result = sut.findAll(null, from, to);
        verifyCommentInterval(from, to, result);
    }

    @Test
    void findAllByAssetAndTimeIntervalReturnsCommentsEditedInSpecifiedTimeInterval() {
        final List<Comment> comments = IntStream.range(0, 10).mapToObj(i -> generateComment(Generator.generateUri()))
                                                .collect(
                                                        Collectors.toList());
        final EntityDescriptor descriptor = createDescriptor();
        transactional(() -> comments.forEach(c -> em.persist(c, descriptor)));
        transactional(() -> {
            for (int i = 0; i < comments.size(); i++) {
                final Comment c = comments.get(i);
                em.createNativeQuery("INSERT DATA { GRAPH ?g { ?c ?modified ?timestamp } }")
                  .setParameter("g", descriptor.getSingleContext().get())
                  .setParameter("c", c)
                  .setParameter("modified", URI.create(Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace))
                  .setParameter("timestamp", Utils.timestamp().minus(i, ChronoUnit.DAYS))
                  .executeUpdate();
            }
        });
        final Instant from = Utils.timestamp().minus(comments.size() / 2, ChronoUnit.DAYS);
        final Instant to = Utils.timestamp().minus(1, ChronoUnit.DAYS);

        final List<Comment> result = sut.findAll(null, from, to);
        verifyCommentInterval(from, to, result);
    }
}
