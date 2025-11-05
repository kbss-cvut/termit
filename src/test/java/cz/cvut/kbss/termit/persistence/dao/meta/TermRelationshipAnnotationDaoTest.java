package cz.cvut.kbss.termit.persistence.dao.meta;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

class TermRelationshipAnnotationDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermRelationshipAnnotationDao sut;

    @Test
    void findAllForSubjectRetrievesAnnotationsForGivenTerm() {
        final URI subjectUri = URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/subject");
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

        final Term term = em.find(Term.class, subjectUri);
        final List<TermRelationshipAnnotation> result = sut.findAllForSubject(term);
        assertThat(result, hasItem(new TermRelationshipAnnotation(
                new RdfStatement(subjectUri, URI.create(SKOS.BROADER),
                                 URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/object")),
                URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/approved"),
                true)));
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
        final URI objectUri = URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/object");
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

        final Term term = em.find(Term.class, objectUri);
        final List<TermRelationshipAnnotation> result = sut.findAllForSubject(term);
        assertThat(result, hasItem(new TermRelationshipAnnotation(
                new RdfStatement(objectUri, URI.create(SKOS.RELATED),
                                 URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/subject")),
                URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/approved"),
                true)));
    }
}
