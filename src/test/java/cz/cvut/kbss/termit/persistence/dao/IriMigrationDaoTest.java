package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IriMigrationDaoTest extends BaseDaoTestRunner {
    @Autowired
    private EntityManager em;

    @Autowired
    private IriMigrationDao sut;

    private IriMigrationPair iris;

    @BeforeEach
    void setUp() {
        iris = new IriMigrationPair(Generator.generateUri(), Generator.generateUri());
    }

    /**
     * Provides the subject, predicate and object data to insert into a graph and Iri migration pairs with subject,
     * predicate and object as originalIri respectively.
     */
    static Stream<Arguments> migrateIdentifierSource() {
        URI subject = Generator.generateUri();
        URI predicate = Generator.generateUri();
        URI object = Generator.generateUri();
        URI newUri = Generator.generateUri();
        return Stream.of(
                Arguments.of(subject, predicate, object, new IriMigrationPair(subject, newUri)),
                Arguments.of(subject, predicate, object, new IriMigrationPair(predicate, newUri)),
                Arguments.of(subject, predicate, object, new IriMigrationPair(object, newUri)));
    }

    private boolean askExists(URI subject, URI predicate, URI object, URI graph) {
        return readOnlyTransactional(() -> {
            TypedQuery<Boolean> query = em.createNativeQuery(
                    graph == null
                            ? "ASK { ?subject ?predicate ?object }"
                            : "ASK { GRAPH ?graph { ?subject ?predicate ?object }}",
                    Boolean.class);

            query.setParameter("subject", subject)
                    .setParameter("predicate", predicate)
                    .setParameter("object", object);

            if (graph != null) {
                query.setParameter("graph", graph);
            }

            return query.getSingleResult();
        });
    }

    /**
     * Checks whether the given URI matches the URI for migration, if so returns the new IRI.
     *
     * @param uri the URI to check
     * @param iris the pair of URIs for migration
     * @return {@link IriMigrationPair#newIri()} when the {@code uri} matches the
     *     {@link IriMigrationPair#originalIri()}, {@code uri} otherwise.
     */
    private static URI migrated(URI uri, IriMigrationPair iris) {
        if (uri.equals(iris.originalIri())) {
            return iris.newIri();
        }
        return uri;
    }

    private boolean askExistsAfterMigration(URI subject, URI predicate, URI object, URI graph, IriMigrationPair iris) {
        return askExists(migrated(subject, iris), migrated(predicate, iris), migrated(object, iris), graph);
    }

    private void insertData(URI subject, URI predicate, URI object, URI graph) {
        transactional(() -> {
            Query query = em.createNativeQuery(graph == null ? "INSERT DATA { ?subject ?predicate ?object }" : """
                        INSERT DATA {
                            GRAPH ?graph {
                                ?subject ?predicate ?object .
                            }
                        }
                    """);
            query.setParameter("subject", subject)
                    .setParameter("predicate", predicate)
                    .setParameter("object", object);

            if (graph != null) {
                query.setParameter("graph", graph);
            }

            query.executeUpdate();
        });
    }

    @ParameterizedTest
    @MethodSource("migrateIdentifierSource")
    void migrateIdentifierReplacesIriInNamedGraph(URI subject, URI predicate, URI object, IriMigrationPair iris) {
        final URI graph = Generator.generateUri();

        insertData(subject, predicate, object, graph);

        transactional(() -> {
            sut.migrateIdentifier(iris);
        });

        assertFalse(askExists(subject, predicate, object, graph));
        assertTrue(askExistsAfterMigration(subject, predicate, object, graph, iris));
    }

    @ParameterizedTest
    @MethodSource("migrateIdentifierSource")
    void migrateIdentifierReplacesIriInDefaultGraph(URI subject, URI predicate, URI object, IriMigrationPair iris) {
        insertData(subject, predicate, object, null);

        transactional(() -> {
            sut.migrateIdentifier(iris);
        });

        assertFalse(askExists(subject, predicate, object, null));
        assertFalse(askExists(subject, predicate, object, Constants.DEFAULT_GRAPH));
        assertTrue(askExistsAfterMigration(subject, predicate, object, null, iris));
    }
}
