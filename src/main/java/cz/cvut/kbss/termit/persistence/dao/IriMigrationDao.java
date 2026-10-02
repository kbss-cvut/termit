package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.stereotype.Repository;

@Repository
public class IriMigrationDao {
    private static final String QUERY_DIR = "iri-migration/";
    private final EntityManager em;

    public IriMigrationDao(EntityManager em) {
        this.em = em;
    }

    /**
     * Loads named query from {@code .rq} file located in {@link #QUERY_DIR query directory}.
     *
     * @param name the name of the query file without extension
     * @return Contents of the query file
     */
    private static String loadQuery(String name) {
        return Utils.loadQuery(QUERY_DIR + name + ".rq");
    }

    /**
     * Replaces any occurrence of the {@link IriMigrationPair#originalIri()} with the {@link IriMigrationPair#newIri()}.
     * <p>
     * Snapshot modification is prevented except of rewiring the {@code is-version-of} relations.
     *
     * @param iris the pair of IRIs for migration
     */
    public void migrateIdentifier(IriMigrationPair iris) {
        try {
            Query query = em.createNativeQuery(loadQuery("identifierMigration"));
            bind(query, iris);
            query.executeUpdate(); // execute for all named graphs

            query.setParameter("graph", Constants.DEFAULT_GRAPH);
            query.executeUpdate(); // execute only for the default graph
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to migrate identifier: " + iris, e);
        }
    }

    /**
     * Binds {@code ?originalIri} and {@code ?newIri} from the {@link IriMigrationPair}.
     *
     * @param query the query to bind parameters to
     * @param iris the pair of IRIs for migration
     */
    private static void bind(Query query, IriMigrationPair iris) {
        query.setParameter("originalIri", iris.originalIri())
             .setParameter("newIri", iris.newIri());
    }
}
