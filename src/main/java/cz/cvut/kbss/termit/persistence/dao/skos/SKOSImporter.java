package cz.cvut.kbss.termit.persistence.dao.skos;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.DataImportException;
import cz.cvut.kbss.termit.exception.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The tool to import plain SKOS thesauri.
 * <p>
 * It takes the thesauri as a TermIt glossary and 1) creates the necessary metadata (vocabulary, model) 2) generates the
 * necessary hasTopConcept relationships based on the broader/narrower hierarchy.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SKOSImporter {

    private static final Logger LOG = LoggerFactory.getLogger(SKOSImporter.class);

    private final Configuration config;
    private final VocabularyDao vocabularyDao;

    private final Repository repository;
    private final ValueFactory vf;

    private final Model model = new LinkedHashModel();

    private IRI glossary;

    @Autowired
    public SKOSImporter(Configuration config,
                        VocabularyDao vocabularyDao,
                        EntityManager em) {
        this.config = config;
        this.vocabularyDao = vocabularyDao;
        this.repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        vf = repository.getValueFactory();
    }

    public Vocabulary importVocabulary(String vocabularyIri, String mediaType,
                                       InputStream... inputStreams) {
        Objects.requireNonNull(vocabularyIri);
        if (inputStreams.length == 0) {
            throw new IllegalArgumentException("No input provided for importing vocabulary.");
        }
        LOG.debug("Vocabulary import started.");
        parseDataFromStreams(mediaType, inputStreams);
        resolveGlossary();
        LOG.trace("Glossary identifier resolved to {}.", glossary);
        insertTopConceptAssertions();
        Vocabulary vocabulary = resolveVocabularyFromGlossary(vocabularyIri);
        vocabularyDao.persist(vocabulary);
        addDataIntoRepository(vocabularyIri);
        LOG.debug("Vocabulary import successfully finished.");
        return vocabulary;
    }

    private void parseDataFromStreams(String mediaType, InputStream... inputStreams) {
        final RDFFormat rdfFormat = Rio.getParserFormatForMIMEType(mediaType).orElseThrow(
                () -> new UnsupportedImportMediaTypeException(
                        "Media type" + mediaType + "not supported."));
        final RDFParser p = Rio.createParser(rdfFormat);
        final StatementCollector collector = new StatementCollector(model);
        p.setRDFHandler(collector);
        for (InputStream is : inputStreams) {
            try {
                p.parse(is, "");
            } catch (IOException e) {
                throw new DataImportException("Unable to parse data for import.", e);
            }
        }
    }

    private void addDataIntoRepository(String vocabularyIri) {
        try (final RepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            final IRI targetContext = vf.createIRI(vocabularyIri);
            LOG.debug("Importing vocabulary into context <{}>.", targetContext);
            conn.add(model, targetContext);
            conn.commit();
        }
    }

    private void resolveGlossary() {
        final Model glossaryRes = model.filter(null, RDF.TYPE, SKOS.CONCEPT_SCHEME);
        if (glossaryRes.size() == 1) {
            final Resource glossary = glossaryRes.iterator().next().getSubject();
            if (glossary.isIRI()) {
                this.glossary = (IRI) glossary;
            } else {
                throw new IllegalArgumentException(
                        "Blank node skos:ConceptScheme not supported.");
            }
        } else {
            throw new IllegalArgumentException(
                    "No unique skos:ConceptScheme found in the provided data.");
        }
    }

    private Vocabulary resolveVocabularyFromGlossary(final String vocabularyIri) {
        final Vocabulary instance = new Vocabulary();
        instance.setUri(URI.create(vocabularyIri));

        final Glossary gls = new Glossary();
        gls.setUri(URI.create(glossary.stringValue()));
        instance.setGlossary(gls);

        final cz.cvut.kbss.termit.model.Model mdl = new cz.cvut.kbss.termit.model.Model();
        mdl.setUri(URI.create(vocabularyIri + "/model"));
        instance.setModel(mdl);

        final Set<Statement> labels = model.filter(glossary, DCTERMS.TITLE, null);
        labels.stream().filter(s -> {
            assert s.getObject() instanceof Literal;
            return Objects.equals(config.get(ConfigParam.LANGUAGE),
                    ((Literal) s.getObject()).getLanguage().orElse(config.get(ConfigParam.LANGUAGE)));
        }).findAny().ifPresent(s -> instance.setLabel(s.getObject().stringValue()));
        return instance;
    }

    private void insertTopConceptAssertions() {
        LOG.trace("Generating top concept assertions.");
        final List<Resource> terms = model.filter(null, RDF.TYPE, SKOS.CONCEPT).stream().map
                (Statement::getSubject)
                .collect(Collectors.toList());
        terms.forEach(t -> {
            final List<Value> broader = model.filter(t, SKOS.BROADER, null).stream().map
                    (Statement::getObject)
                    .collect(Collectors.toList());
            final boolean hasBroader = broader.stream()
                    .anyMatch(p -> model.contains((Resource) p, RDF
                            .TYPE, SKOS.CONCEPT));
            final List<Value> narrower = model.filter(null, SKOS.NARROWER, t).stream().map
                    (Statement::getObject)
                    .collect(Collectors.toList());
            final boolean isNarrower = narrower.stream()
                    .anyMatch(p -> model.contains((Resource) p, RDF
                            .TYPE, SKOS.CONCEPT));
            if (!hasBroader && !isNarrower) {
                model.add(glossary, SKOS.HAS_TOP_CONCEPT, t);
            }
        });
    }
}
