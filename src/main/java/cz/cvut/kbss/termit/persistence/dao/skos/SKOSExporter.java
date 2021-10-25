package cz.cvut.kbss.termit.persistence.dao.skos;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Utils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.*;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Supports SKOS-based export of glossaries and terms.
 */
@Repository
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SKOSExporter {

    private static final String GLOSSARY_EXPORT_QUERY = "skos" + File.separator + "exportGlossary.rq";
    private static final String TERMS_EXPORT_QUERY = "skos" + File.separator + "exportGlossaryTerms.rq";

    private final org.eclipse.rdf4j.repository.Repository repository;
    private final ValueFactory vf;

    private final Model model = new LinkedHashModel();

    @Autowired
    public SKOSExporter(EntityManager em) {
        this.repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        vf = repository.getValueFactory();
    }

    /**
     * Exports metadata of the glossary of the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose glossary to export
     */
    public void exportGlossaryInstance(Vocabulary vocabulary) {
        try (final RepositoryConnection conn = repository.getConnection()) {
            final GraphQuery gq = conn.prepareGraphQuery(Utils.loadQuery(GLOSSARY_EXPORT_QUERY));
            gq.setBinding("vocabulary", vf.createIRI(vocabulary.getUri().toString()));
            evaluateAndAddToModel(gq);
            resolvePrefixes(vf.createIRI(vocabulary.getGlossary().getUri().toString()), conn);
        }
    }

    private void evaluateAndAddToModel(GraphQuery gq) {
        try (GraphQueryResult gqResult = gq.evaluate()) {
            while (gqResult.hasNext()) {
                model.add(gqResult.next());
            }
        }
    }

    private void resolvePrefixes(IRI glossaryIri, RepositoryConnection connection) {
        final TupleQuery tq = connection.prepareTupleQuery("SELECT ?prefix ?namespace WHERE {\n" +
                "?glossary <http://purl.org/vocab/vann/preferredNamespacePrefix> ?prefix ;\n" +
                "<http://purl.org/vocab/vann/preferredNamespaceUri> ?namespace .\n" +
                "}");
        tq.setBinding("glossary", glossaryIri);
        final TupleQueryResult result = tq.evaluate();
        while (result.hasNext()) {
            final BindingSet binding = result.next();
            model.setNamespace(binding.getValue("prefix").stringValue(), binding.getValue("namespace").stringValue());
        }
        model.setNamespace(SKOS.PREFIX, SKOS.NAMESPACE);
        model.setNamespace(RDFS.PREFIX, RDFS.NAMESPACE);
        model.setNamespace(OWL.PREFIX, OWL.NAMESPACE);
        model.setNamespace(DCTERMS.PREFIX, DCTERMS.NAMESPACE);
    }

    /**
     * Exports glossary terms of the specified vocabulary.
     *
     * @param vocabulary Vocabulary to export
     */
    public void exportGlossaryTerms(Vocabulary vocabulary) {
        try (final RepositoryConnection conn = repository.getConnection()) {
            final GraphQuery gq = conn.prepareGraphQuery(Utils.loadQuery(TERMS_EXPORT_QUERY));
            gq.setBinding("vocabulary", vf.createIRI(vocabulary.getUri().toString()));
            evaluateAndAddToModel(gq);
        }
    }

    /**
     * Exports terms referenced by terms from the previously exported glossary terms ({@link #exportGlossaryTerms(Vocabulary)}) via
     * one of the specified properties.
     * <p>
     * Note that this expects {@link #exportGlossaryTerms(Vocabulary)} to have been called prior to this method.
     *
     * @param properties SKOS properties representing reference to external terms, e.g., skos:exactMatch
     */
    public void exportReferencedTerms(Collection<String> properties) {
        Objects.requireNonNull(properties);
        if (properties.isEmpty()) {
            return;
        }
        try (final RepositoryConnection conn = repository.getConnection()) {
            final GraphQuery gq = conn.prepareGraphQuery(Utils.loadQuery(TERMS_EXPORT_QUERY));
            properties.forEach(p -> {
                final IRI property = vf.createIRI(p);
                final Set<IRI> referencedTerms = model.stream().filter(s -> s.getPredicate().equals(property))
                                                      .map(s -> {
                                                          assert s.getObject().isIRI();
                                                          return (IRI) s.getObject();
                                                      }).collect(Collectors.toSet());
                referencedTerms.forEach(referencedTerm -> {
                    gq.setBinding("term", referencedTerm);
                    evaluateAndAddToModel(gq);
                });
            });
        }
    }

    /**
     * Exports metadata of glossaries containing the referenced external terms as discovered by {@link #exportReferencedTerms(Collection)}.
     */
    public void exportReferencedGlossaries() {
        final Set<IRI> glossariesToExport = model.stream().filter(s -> s.getPredicate().equals(SKOS.IN_SCHEME))
                                                 .map(s -> {
                                                     assert s.getObject().isIRI();
                                                     return (IRI) s.getObject();
                                                 }).filter(gIri -> !model.contains(gIri, RDF.TYPE, SKOS.CONCEPT_SCHEME))
                                                 .collect(Collectors.toSet());
        try (final RepositoryConnection conn = repository.getConnection()) {
            final GraphQuery gq = conn.prepareGraphQuery(Utils.loadQuery(GLOSSARY_EXPORT_QUERY));
            glossariesToExport.forEach(gIri -> {
                gq.setBinding("glossary", gIri);
                evaluateAndAddToModel(gq);
                resolvePrefixes(gIri, conn);
            });
        }
    }

    /**
     * Returns the exported model as Turtle.
     *
     * @return Turtle serialized into bytes
     */
    public byte[] exportAsTtl() {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Rio.write(model, bos, RDFFormat.TURTLE);
        return bos.toByteArray();
    }
}
