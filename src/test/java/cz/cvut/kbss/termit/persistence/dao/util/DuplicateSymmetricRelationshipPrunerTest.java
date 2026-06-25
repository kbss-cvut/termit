package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateSymmetricRelationshipPrunerTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @AfterEach
    void tearDown() {
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (RepositoryConnection conn = repo.getConnection()) {
                conn.clear();
            }
        });
    }

    @Test
    void pruneRemovesDuplicateSymmetricRelationshipsFromDefaultContext() {
        transactional(() -> em.createNativeQuery("""
                                                         INSERT DATA {
                                                               <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/2> .
                                                               <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/2> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> .
                                                               <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/3> .
                                                         }
                                                         """).executeUpdate());
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (RepositoryConnection conn = repo.getConnection()) {
                new DuplicateSymmetricRelationshipPruner(conn).prune();
            }
        });
        assertEquals(2, em.createNativeQuery(
                "SELECT (COUNT(*) as ?cnt) WHERE { ?x <http://www.w3.org/2004/02/skos/core#exactMatch> ?y }",
                Integer.class).getSingleResult());
        assertTrue(em.createNativeQuery(
                "ASK WHERE { <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/3> }",
                Boolean.class).getSingleResult());
    }

    @Test
    void pruneRemovesDuplicateSymmetricRelationshipsFromVocabularyContexts() {
        transactional(() -> em.createNativeQuery("""
                                                         INSERT DATA {
                                                            GRAPH <http://onto.fel.cvut.cz/ontologies/slovnik/1> {
                                                               <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/2> .
                                                               <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/3> .
                                                            }
                                                            GRAPH <http://onto.fel.cvut.cz/ontologies/slovnik/2> {
                                                               <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/2> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> .
                                                            }
                                                         }
                                                         """).executeUpdate());
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (RepositoryConnection conn = repo.getConnection()) {
                new DuplicateSymmetricRelationshipPruner(conn).prune();
            }
        });
        assertEquals(2, em.createNativeQuery(
                "SELECT (COUNT(*) as ?cnt) WHERE { ?x <http://www.w3.org/2004/02/skos/core#exactMatch> ?y }",
                Integer.class).getSingleResult());
        assertTrue(em.createNativeQuery(
                "ASK WHERE { <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/1> <http://www.w3.org/2004/02/skos/core#exactMatch> <http://onto.fel.cvut.cz/ontologies/slovnik/pojem/3> }",
                Boolean.class).getSingleResult());
    }
}
