package cz.cvut.kbss.termit.persistence.dao.meta;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.dto.meta.AnnotatedTermRelationship;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermRelationshipAnnotationDaoTest extends BaseDaoTestRunner {

    private static final URI SUBJECT = URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/subject");
    private static final URI OBJECT = URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/object");
    private static final URI ANNOTATION_PROPERTY =
            URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/approved");

    @Autowired
    private EntityManager em;

    @Autowired
    private TermRelationshipAnnotationDao sut;

    @Test
    void findAllForSubjectRetrievesAnnotationsForGivenTerm() {
        final String data = """
                <http://onto.fel.cvut.cz/ontologies/application/termit> {
                <http://onto.fel.cvut.cz/ontologies/application/termit> a <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/slovník> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/subject> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Subject"@en ;
                    <http://www.w3.org/2004/02/skos/core#broader> <http://onto.fel.cvut.cz/ontologies/application/termit/object> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/object> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Object"@en .
                << <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://www.w3.org/2004/02/skos/core#broader> <http://onto.fel.cvut.cz/ontologies/application/termit/object> >> <http://onto.fel.cvut.cz/ontologies/application/termit/approved> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/custom-attributes> {
                    <http://onto.fel.cvut.cz/ontologies/application/termit/approved> a <http://onto.fel.cvut.cz/ontologies/application/termit/pojem/vlastní-atribut> ;
                        rdfs:label "Approved"@en ;
                        rdfs:domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                <http://onto.fel.cvut.cz/ontologies/application/termit/object> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                """;
        transactional(() -> loadData(data));

        final Term term = em.find(Term.class, SUBJECT);
        final List<TermRelationshipAnnotation> result = sut.findAllForSubject(term);
        assertThat(result, hasItem(new TermRelationshipAnnotation(
                new RdfStatement(SUBJECT, URI.create(SKOS.BROADER), OBJECT),
                ANNOTATION_PROPERTY, true)));
    }

    private void loadData(String data) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.add(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), null, RDFFormat.TRIGSTAR);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load test data.", e);
        }
    }

    @Test
    void findAllForSubjectRetrievesAnnotationsOfSymmetricRelationshipsWhereGivenTermIsObject() {
        final String data = """
                <http://onto.fel.cvut.cz/ontologies/application/termit> {
                <http://onto.fel.cvut.cz/ontologies/application/termit> a <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/slovník> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/subject> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Subject"@en ;
                    <http://www.w3.org/2004/02/skos/core#broader> <http://onto.fel.cvut.cz/ontologies/application/termit/object> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/object> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Object"@en .
                << <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://www.w3.org/2004/02/skos/core#related> <http://onto.fel.cvut.cz/ontologies/application/termit/object> >> <http://onto.fel.cvut.cz/ontologies/application/termit/approved> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/custom-attributes> {
                    <http://onto.fel.cvut.cz/ontologies/application/termit/approved> a <http://onto.fel.cvut.cz/ontologies/application/termit/pojem/vlastní-atribut> ;
                        rdfs:label "Approved"@en ;
                        rdfs:domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                <http://onto.fel.cvut.cz/ontologies/application/termit/object> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                """;
        transactional(() -> loadData(data));

        final Term term = em.find(Term.class, OBJECT);
        final List<TermRelationshipAnnotation> result = sut.findAllForSubject(term);
        assertThat(result, hasItem(new TermRelationshipAnnotation(
                new RdfStatement(OBJECT, URI.create(SKOS.RELATED), SUBJECT),
                ANNOTATION_PROPERTY, true)));
    }

    @Test
    void updateTermRelationshipAnnotationSavesNewValuesOfSpecifiedRelationshipAnnotation() {
        final String data = """
                <http://onto.fel.cvut.cz/ontologies/application/termit> {
                <http://onto.fel.cvut.cz/ontologies/application/termit> a <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/slovník> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/subject> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Subject"@en ;
                    <http://www.w3.org/2004/02/skos/core#related> <http://onto.fel.cvut.cz/ontologies/application/termit/object> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/object> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Object"@en .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/custom-attributes> {
                    <http://onto.fel.cvut.cz/ontologies/application/termit/approved> a <http://onto.fel.cvut.cz/ontologies/application/termit/pojem/vlastní-atribut> ;
                        rdfs:label "Approved"@en ;
                        rdfs:domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                <http://onto.fel.cvut.cz/ontologies/application/termit/object> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                """;
        transactional(() -> loadData(data));
        final TermRelationshipAnnotation annotation =
                new TermRelationshipAnnotation(new RdfStatement(SUBJECT, URI.create(SKOS.RELATED), OBJECT),
                                               ANNOTATION_PROPERTY, true);

        transactional(() -> sut.updateTermRelationshipAnnotation(annotation));

        readOnlyTransactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection conn = repo.getConnection()) {
                assertTrue(
                        conn.hasStatement(vf.createTriple(vf.createIRI(SUBJECT.toString()), vf.createIRI(SKOS.RELATED),
                                                          vf.createIRI(OBJECT.toString())),
                                          vf.createIRI(ANNOTATION_PROPERTY.toString()), vf.createLiteral(true), false,
                                          vf.createIRI("http://onto.fel.cvut.cz/ontologies/application/termit")));
            }
        });
    }

    @Test
    void updateTermRelationshipAnnotationReplacesExistingValues() {
        final String data = """
                <http://onto.fel.cvut.cz/ontologies/application/termit> {
                <http://onto.fel.cvut.cz/ontologies/application/termit> a <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/slovník> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/subject> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Subject"@en ;
                    <http://www.w3.org/2004/02/skos/core#related> <http://onto.fel.cvut.cz/ontologies/application/termit/object> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/object> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Object"@en .
                << <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://www.w3.org/2004/02/skos/core#related> <http://onto.fel.cvut.cz/ontologies/application/termit/object> >> <http://onto.fel.cvut.cz/ontologies/application/termit/approved> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/custom-attributes> {
                    <http://onto.fel.cvut.cz/ontologies/application/termit/approved> a <http://onto.fel.cvut.cz/ontologies/application/termit/pojem/vlastní-atribut> ;
                        rdfs:label "Approved"@en ;
                        rdfs:domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                <http://onto.fel.cvut.cz/ontologies/application/termit/object> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                """;
        transactional(() -> loadData(data));

        final TermRelationshipAnnotation annotation =
                new TermRelationshipAnnotation(new RdfStatement(SUBJECT, URI.create(SKOS.RELATED), OBJECT),
                                               ANNOTATION_PROPERTY, false);
        transactional(() -> sut.updateTermRelationshipAnnotation(annotation));

        readOnlyTransactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection conn = repo.getConnection()) {
                assertFalse(
                        conn.hasStatement(vf.createTriple(vf.createIRI(SUBJECT.toString()), vf.createIRI(SKOS.RELATED),
                                                          vf.createIRI(OBJECT.toString())),
                                          vf.createIRI(ANNOTATION_PROPERTY.toString()), vf.createLiteral(true), false,
                                          vf.createIRI("http://onto.fel.cvut.cz/ontologies/application/termit")));
                assertTrue(
                        conn.hasStatement(vf.createTriple(vf.createIRI(SUBJECT.toString()), vf.createIRI(SKOS.RELATED),
                                                          vf.createIRI(OBJECT.toString())),
                                          vf.createIRI(ANNOTATION_PROPERTY.toString()), vf.createLiteral(false), false,
                                          vf.createIRI("http://onto.fel.cvut.cz/ontologies/application/termit")));
            }
        });
    }

    @Test
    void updateTermRelationshipAnnotationSupportsResolvingContextWhenSubjectTermIsActuallyObjectOfAssertedStatement() {
        final String data = """
                <http://onto.fel.cvut.cz/ontologies/application/termit> {
                <http://onto.fel.cvut.cz/ontologies/application/termit> a <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/slovník> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/subject> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Subject"@en .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/object> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Object"@en ;
                    <http://www.w3.org/2004/02/skos/core#related> <http://onto.fel.cvut.cz/ontologies/application/termit/subject> .
                << <http://onto.fel.cvut.cz/ontologies/application/termit/object> <http://www.w3.org/2004/02/skos/core#related> <http://onto.fel.cvut.cz/ontologies/application/termit/subject> >> <http://onto.fel.cvut.cz/ontologies/application/termit/approved> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/custom-attributes> {
                    <http://onto.fel.cvut.cz/ontologies/application/termit/approved> a <http://onto.fel.cvut.cz/ontologies/application/termit/pojem/vlastní-atribut> ;
                        rdfs:label "Approved"@en ;
                        rdfs:domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                <http://onto.fel.cvut.cz/ontologies/application/termit/object> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                """;
        transactional(() -> loadData(data));

        final TermRelationshipAnnotation annotation =
                new TermRelationshipAnnotation(new RdfStatement(SUBJECT, URI.create(SKOS.RELATED), OBJECT),
                                               ANNOTATION_PROPERTY, false);
        transactional(() -> sut.updateTermRelationshipAnnotation(annotation));

        readOnlyTransactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection conn = repo.getConnection()) {
                assertFalse(
                        conn.hasStatement(vf.createTriple(vf.createIRI(OBJECT.toString()), vf.createIRI(SKOS.RELATED),
                                                          vf.createIRI(SUBJECT.toString())),
                                          vf.createIRI(ANNOTATION_PROPERTY.toString()), vf.createLiteral(true), false,
                                          vf.createIRI("http://onto.fel.cvut.cz/ontologies/application/termit")));
                assertTrue(
                        conn.hasStatement(vf.createTriple(vf.createIRI(OBJECT.toString()), vf.createIRI(SKOS.RELATED),
                                                          vf.createIRI(SUBJECT.toString())),
                                          vf.createIRI(ANNOTATION_PROPERTY.toString()), vf.createLiteral(false), false,
                                          vf.createIRI("http://onto.fel.cvut.cz/ontologies/application/termit")));
            }
        });
    }

    @Test
    void getRelationshipsAnnotatedByTermRetrievesRelationshipsAnnotatedByGivenTerm() {
        final String data = """
                <http://onto.fel.cvut.cz/ontologies/application/termit> {
                <http://onto.fel.cvut.cz/ontologies/application/termit> a <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/slovník> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/subject> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Subject"@en ;
                    <http://www.w3.org/2004/02/skos/core#broader> <http://onto.fel.cvut.cz/ontologies/application/termit/object> .
                  <http://onto.fel.cvut.cz/ontologies/application/termit/object> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                    <http://www.w3.org/2004/02/skos/core#prefLabel> "Object"@en .
                << <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://www.w3.org/2004/02/skos/core#broader> <http://onto.fel.cvut.cz/ontologies/application/termit/object> >> <http://onto.fel.cvut.cz/ontologies/application/termit/annotated-by> <http://onto.fel.cvut.cz/ontologies/application/termit/annotating-term> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/custom-attributes> {
                    <http://onto.fel.cvut.cz/ontologies/application/termit/annotated-by> a <http://onto.fel.cvut.cz/ontologies/application/termit/pojem/vlastní-atribut> ;
                        rdfs:label "Annotated by"@en ;
                        rdfs:domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement> .
                }
                <http://onto.fel.cvut.cz/ontologies/application/termit/subject> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                <http://onto.fel.cvut.cz/ontologies/application/termit/object> <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/je-pojmem-ze-slovníku> <http://onto.fel.cvut.cz/ontologies/application/termit> .
                """;
        transactional(() -> loadData(data));

        final Term annotatingTerm = new Term(
                URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/annotating-term"));
        final List<AnnotatedTermRelationship> result = sut.getRelationshipsAnnotatedByTerm(annotatingTerm);
        assertEquals(1, result.size());
        assertEquals(SUBJECT, result.get(0).getSubject().getUri());
        assertEquals(OBJECT, result.get(0).getObject().getUri());
        assertEquals(URI.create(SKOS.BROADER), result.get(0).getProperty());
        assertEquals(URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/annotated-by"),
                     result.get(0).getAnnotationProperty());
    }
}
