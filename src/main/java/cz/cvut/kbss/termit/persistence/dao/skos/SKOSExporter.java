/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao.skos;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.service.export.ExportFormat;
import cz.cvut.kbss.termit.util.Utils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.rdfxml.util.RDFXMLPrettyWriterFactory;
import org.eclipse.rdf4j.rio.turtle.TurtleWriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
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

    private static final Logger LOG = LoggerFactory.getLogger(SKOSExporter.class);

    private static final String VOCABULARY_EXPORT_QUERY = "export/skos/exportVocabulary.rq";
    private static final String TERMS_EXPORT_QUERY = "export/skos/exportVocabularyTerms.rq";
    private static final String TERMS_FULL_EXPORT_QUERY = "export/full/exportVocabularyTerms.rq";
    private static final String TERMS_HIERARCHY_EXPORT_QUERY = "export/full/exportHierarchy.rq";

    private final VocabularyContextMapper vocabularyContextMapper;

    private final org.eclipse.rdf4j.repository.Repository repository;
    private final ValueFactory vf;

    private final Model model = new TreeModel();

    @Autowired
    public SKOSExporter(VocabularyContextMapper vocabularyContextMapper, EntityManager em) {
        this.vocabularyContextMapper = vocabularyContextMapper;
        this.repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        vf = repository.getValueFactory();
    }

    /**
     * Exports vocabulary and terms of the specified vocabulary as a SKOS model.
     * <p>
     * Only basic SKOS properties are exported for terms. To get all available data, use
     * {@link #exportFullVocabulary(Vocabulary)}.
     * <p>
     * The exported data can be retrieved using {@link #exportAs(ExportFormat)}.
     *
     * @param vocabulary Vocabulary to export
     * @see #exportAs(ExportFormat)
     * @see #exportVocabularyWithReferences(Vocabulary, Collection)
     */
    public void exportVocabulary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        exportVocabularyInstance(vocabulary);
        exportVocabularyTermsWithQuery(vocabulary, TERMS_EXPORT_QUERY);
    }

    /**
     * Exports vocabulary and terms of the specified vocabulary as a SKOS model.
     * <p>
     * This method exports all available asserted data for each term.
     * <p>
     * The exported data can be retrieved using {@link #exportAs(ExportFormat)}.
     *
     * @param vocabulary Vocabulary to export
     * @see #exportAs(ExportFormat)
     */
    public void exportFullVocabulary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        exportVocabularyInstance(vocabulary);
        exportVocabularyTermsWithQuery(vocabulary, TERMS_FULL_EXPORT_QUERY);
    }

    /**
     * Exports metadata of the vocabulary of the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose vocabulary to export
     */
    private void exportVocabularyInstance(Vocabulary vocabulary) {
        LOG.trace("Exporting metadata of {}.", vocabulary);
        try (final RepositoryConnection conn = repository.getConnection()) {
            final GraphQuery gq = conn.prepareGraphQuery(Utils.loadQuery(VOCABULARY_EXPORT_QUERY));
            gq.setBinding("vocabulary", vf.createIRI(vocabulary.getUri().toString()));
            evaluateAndAddToModel(gq);
            resolvePrefixes(vf.createIRI(vocabulary.getUri().toString()), conn);
        }
    }

    private void evaluateAndAddToModel(GraphQuery gq) {
        try (GraphQueryResult gqResult = gq.evaluate()) {
            while (gqResult.hasNext()) {
                model.add(gqResult.next());
            }
        }
    }

    private void resolvePrefixes(IRI vocabularyIri, RepositoryConnection connection) {
        final TupleQuery tq = connection.prepareTupleQuery("""
                                                                   SELECT ?prefix ?namespace WHERE {
                                                                   ?vocabulary ?hasPreferredPrefix ?prefix ;
                                                                   ?hasPreferredNamespace ?namespace .
                                                                   }""");
        tq.setBinding("vocabulary", vocabularyIri);
        tq.setBinding("hasPreferredPrefix",
                      vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespacePrefix));
        tq.setBinding("hasPreferredNamespace",
                      vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
        try (final TupleQueryResult result = tq.evaluate()) {
            while (result.hasNext()) {
                final BindingSet binding = result.next();
                model.setNamespace(binding.getValue("prefix").stringValue(),
                                   binding.getValue("namespace").stringValue());
            }
        }
        model.setNamespace(SKOS.PREFIX, SKOS.NAMESPACE);
        model.setNamespace(RDFS.PREFIX, RDFS.NAMESPACE);
        model.setNamespace(OWL.PREFIX, OWL.NAMESPACE);
        model.setNamespace(DCTERMS.PREFIX, DCTERMS.NAMESPACE);
    }

    /**
     * Exports vocabulary terms of the specified vocabulary.
     *
     * @param vocabulary Vocabulary to export
     */
    private void exportVocabularyTermsWithQuery(Vocabulary vocabulary, String queryFile) {
        final long startTime = System.currentTimeMillis();
        LOG.trace("Exporting terms from {}.", vocabulary);
        final IRI graphIri = vf.createIRI(vocabularyContextMapper.getVocabularyContext(vocabulary).toString());
        final IRI vocabularyIri = vf.createIRI(vocabulary.getUri().toString());
        try (final RepositoryConnection conn = repository.getConnection()) {
            GraphQuery gq = conn.prepareGraphQuery(Utils.loadQuery(queryFile));
            gq.setBinding("g", graphIri);
            gq.setBinding("vocabulary", vocabularyIri);
            evaluateAndAddToModel(gq);
            gq = conn.prepareGraphQuery(Utils.loadQuery(TERMS_HIERARCHY_EXPORT_QUERY));
            gq.setBinding("g", graphIri);
            gq.setBinding("vocabulary", vocabularyIri);
            gq.setIncludeInferred(false);
            evaluateAndAddToModel(gq);
        }
        final long endTime = System.currentTimeMillis();
        LOG.debug("Export query evaluation took {} ms.", endTime - startTime);
    }

    /**
     * Exports the vocabulary of the specified vocabulary and its terms.
     * <p>
     * In addition, terms from other vocabularies referenced via the any of the specified properties are exported as
     * well, together with metadata of their respective glossaries.
     *
     * @param vocabulary Vocabulary to export
     * @param properties RDF properties representing references to other terms to take into account when exporting
     * @see #exportAs(ExportFormat)
     */
    public void exportVocabularyWithReferences(Vocabulary vocabulary, Collection<String> properties) {
        Objects.requireNonNull(properties);
        exportVocabulary(vocabulary);
        exportReferencedTermsWithQuery(properties, TERMS_EXPORT_QUERY);
        exportReferencedVocabularies();
    }

    /**
     * Exports the vocabulary of the specified vocabulary and its terms.
     * <p>
     * In addition, terms from other vocabularies referenced via the any of the specified properties are exported as
     * well, together with metadata of their respective glossaries.
     * <p>
     * This method exports all available asserted data for each term.
     *
     * @param vocabulary Vocabulary to export
     * @param properties RDF properties representing references to other terms to take into account when exporting
     * @see #exportAs(ExportFormat)
     */
    public void exportFullVocabularyWithReferences(Vocabulary vocabulary, Collection<String> properties) {
        Objects.requireNonNull(properties);
        exportFullVocabulary(vocabulary);
        exportReferencedTermsWithQuery(properties, TERMS_FULL_EXPORT_QUERY);
        exportReferencedVocabularies();
    }

    /**
     * Exports terms referenced by terms from the previously exported vocabulary terms
     * ({@link #exportVocabularyTermsWithQuery(Vocabulary, String)}) via one of the specified properties.
     *
     * @param properties SKOS properties representing reference to external terms, e.g., skos:exactMatch
     */
    private void exportReferencedTermsWithQuery(Collection<String> properties, String queryFile) {
        if (properties.isEmpty()) {
            return;
        }
        LOG.trace("Exporting terms referenced via any of {}.", properties);
        try (final RepositoryConnection conn = repository.getConnection()) {
            final String queryString = Utils.loadQuery(queryFile);
            properties.forEach(p -> {
                final IRI property = vf.createIRI(p);
                final Set<IRI> referencedTerms = model.stream().filter(s -> s.getPredicate().equals(property))
                                                      .map(s -> {
                                                          assert s.getObject().isIRI();
                                                          return (IRI) s.getObject();
                                                      }).collect(Collectors.toSet());
                referencedTerms.forEach(referencedTerm -> {
                    final GraphQuery gq = conn.prepareGraphQuery(queryString);
                    gq.setBinding("term", referencedTerm);
                    evaluateAndAddToModel(gq);
                });
            });
        }
    }

    /**
     * Exports metadata of glossaries containing the referenced external terms as discovered by
     * {@link #exportReferencedTermsWithQuery(Collection, String)}.
     */
    private void exportReferencedVocabularies() {
        final Set<IRI> vocabulariesToExport = model.stream().filter(s -> s.getPredicate().equals(SKOS.IN_SCHEME))
                                                   .map(s -> {
                                                       assert s.getObject().isIRI();
                                                       return (IRI) s.getObject();
                                                   })
                                                   .filter(vocIri -> !model.contains(vocIri, RDF.TYPE, SKOS.CONCEPT_SCHEME))
                                                   .collect(Collectors.toSet());
        LOG.trace("Exporting metadata of vocabularies of referenced terms: {}.", vocabulariesToExport);
        try (final RepositoryConnection conn = repository.getConnection()) {
            final String queryString = Utils.loadQuery(VOCABULARY_EXPORT_QUERY);
            vocabulariesToExport.forEach(
                    vocIri -> {
                        final GraphQuery gq = conn.prepareGraphQuery(queryString);
                        gq.setBinding("vocabulary", vocIri);
                        evaluateAndAddToModel(gq);
                        resolvePrefixes(vocIri, conn);
                    });
        }
    }

    /**
     * Returns the exported model as Turtle.
     *
     * @return Turtle serialized into bytes
     */
    public byte[] exportAs(ExportFormat format) {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final RDFWriter writer = switch (format) {
            case TURTLE -> new TurtleWriterFactory().getWriter(bos);
            case RDF_XML -> new RDFXMLPrettyWriterFactory().getWriter(bos);
            default -> throw new IllegalArgumentException("Unsupported SKOS export format " + format);
        };
        Rio.write(model, writer);
        return bos.toByteArray();
    }
}
