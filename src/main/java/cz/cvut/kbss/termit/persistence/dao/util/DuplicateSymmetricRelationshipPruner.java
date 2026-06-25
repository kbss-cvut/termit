package cz.cvut.kbss.termit.persistence.dao.util;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Removes duplicate explicit symmetric SKOS relationship assertions from the repository, keeping only one direction.
 * <p>
 * The other direction is inferred by the repository.
 */
public class DuplicateSymmetricRelationshipPruner {

    private static final Logger LOG = LoggerFactory.getLogger(DuplicateSymmetricRelationshipPruner.class);

    private static final List<IRI> SYMMETRIC_RELATIONSHIPS = List.of(
            SKOS.RELATED_MATCH,
            SKOS.EXACT_MATCH
    );

    private final RepositoryConnection connection;

    public DuplicateSymmetricRelationshipPruner(RepositoryConnection connection) {
        this.connection = connection;
    }

    /**
     * Removes duplicate explicit symmetric SKOS relationship assertions from the repository, keeping only one
     * direction.
     */
    public void prune() {
        connection.begin();
        SYMMETRIC_RELATIONSHIPS.forEach(this::pruneRelationship);
        connection.commit();
    }

    private void pruneRelationship(IRI relationship) {
        LOG.trace("Pruning duplicate assertions of <{}>.", relationship);
        final TupleQuery tq = connection.prepareTupleQuery("""
                                                                   SELECT ?x ?y WHERE {
                                                                       ?x ?relationship ?y .
                                                                       ?y ?relationship ?z .
                                                                       FILTER (?x = ?z)
                                                                   }
                                                                   """);
        tq.setBinding("relationship", relationship);
        tq.setIncludeInferred(false);   // Only explicit assertions
        try (final TupleQueryResult res = tq.evaluate()) {
            while (res.hasNext()) {
                final BindingSet bs = res.next();
                final IRI x = (IRI) bs.getValue("x");
                final IRI y = (IRI) bs.getValue("y");
                if (x.stringValue().compareTo(y.stringValue()) > 0) {
                    final Statement statement = Values.getValueFactory()
                                                      .createStatement(x, relationship, y);
                    connection.remove(statement);
                }
            }
        }
    }
}
