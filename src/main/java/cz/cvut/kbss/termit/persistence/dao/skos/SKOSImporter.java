package cz.cvut.kbss.termit.persistence.dao.skos;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.util.Utils.getUniqueIriFromBase;

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
    private final TermDao termDao;

    private final EntityManager em;

    private final Model model = new LinkedHashModel();

    private IRI glossaryIri;

    @Autowired
    public SKOSImporter(Configuration config,
                        VocabularyDao vocabularyDao,
                        TermDao termDao,
                        EntityManager em) {
        this.config = config;
        this.vocabularyDao = vocabularyDao;
        this.termDao = termDao;
        this.em = em;
    }

    /**
     * Imports a SKOS vocabulary from file.
     *
     * @param rename whether to rename IRIs in case a conflicting IRI is found.
     * @param vocabularyIri (Optional) IRI of the vocabulary to import. If not supplied, the IRI is inferred from the data.
     * @param mediaType     media type of the imported input streams
     * @param persist   an implementation of the persist operation (e.g. as performed by a VocabularyService)
     * @param inputStreams  input streams with the SKOS data
     * @return imported vocabulary
     */
    public Vocabulary importVocabulary(final boolean rename,
                                       final URI vocabularyIri,
                                       final String mediaType,
                                       final Consumer<Vocabulary> persist,
                                       final InputStream... inputStreams) {
        if (inputStreams.length == 0) {
            throw new IllegalArgumentException("No input provided for importing vocabulary.");
        }
        LOG.debug("Vocabulary import started.");
        parseDataFromStreams(mediaType, inputStreams);
        glossaryIri = resolveGlossaryIriFromImportedData(model);
        LOG.trace("Importing glossary {}.", glossaryIri);
        insertTopConceptAssertions();

        final String vocabularyIriFromData = resolveVocabularyIriFromImportedData();
        if ( vocabularyIri != null && !vocabularyIri.toString().equals(vocabularyIriFromData) ) {
            throw new IllegalArgumentException("Cannot import a vocabulary into an existing one with different identifier.");
        }

        final Vocabulary vocabulary = createVocabulary(rename, vocabularyIri, vocabularyIriFromData);
        ensureConceptIrisAreCompatibleWithTermIt();

        LOG.trace("New vocabulary {} with a new glossary {}.", vocabulary.getUri(), vocabulary.getGlossary().getUri());

        final Optional<Glossary> glossary = vocabularyDao.findGlossary(vocabulary.getGlossary().getUri());
        if (glossary.isPresent()) {
            throw new IllegalArgumentException("The glossary '" + vocabulary.getGlossary().getUri() + "' already exists.");
        }

        if ( vocabularyIri == null ) {
            final Optional<Vocabulary> possiblyVocabulary = vocabularyDao.find(vocabulary.getUri());
            if (possiblyVocabulary.isPresent()) {
                throw new IllegalArgumentException("The vocabulary IRI '" + vocabulary.getUri() + "' already exists.");
            }
        } else {
            clearVocabulary(vocabularyIri);
        }

        em.flush();
        persist.accept(vocabulary);
        addDataIntoRepository(vocabulary.getUri());
        LOG.debug("Vocabulary import successfully finished.");
        return vocabulary;
    }

    private void clearVocabulary(final URI vocabularyIri) {
        final Optional<Vocabulary> possibleVocabulary = vocabularyDao.find(vocabularyIri);
        if ( possibleVocabulary.isPresent() ) {
            Vocabulary vocabulary = possibleVocabulary.get();
            termDao.findAll(vocabulary).forEach(t -> {
                if (t.getProperties() != null) {
                    t.getProperties().clear();
                }
                termDao.remove(t);
                vocabulary.getGlossary().removeRootTerm(t);
            });
            vocabularyDao.remove(vocabulary);
        }
    }

    private void ensureConceptIrisAreCompatibleWithTermIt() {
        final Statement[] statements = model.filter(null, RDF.TYPE, SKOS.CONCEPT).toArray(new Statement[]{});
        for ( final Statement c : statements ) {
            String separator = config.getNamespace().getTerm().getSeparator();
            if ( c.getSubject().stringValue().contains(separator) ) {
                continue;
            }
            separator = "#";
            if ( !c.getSubject().stringValue().contains(separator) ) {
                separator = "/";
            }
                final String sIri = c.getSubject().stringValue();
                final int lastSeparator = sIri.lastIndexOf(separator);
                final String newIri = sIri.substring(0,lastSeparator)
                    + config.getNamespace().getTerm().getSeparator() + "/"
                    + sIri.substring(lastSeparator + 1);
                Utils.changeIri(c.getSubject().stringValue(), newIri, model);
        }
    }

    private void parseDataFromStreams(String mediaType, InputStream... inputStreams) {
        final RDFFormat rdfFormat = Rio.getParserFormatForMIMEType(mediaType).orElseThrow(
            () -> new UnsupportedImportMediaTypeException(
                "Media type '" + mediaType + "' not supported."));
        final RDFParser p = Rio.createParser(rdfFormat);
        final StatementCollector collector = new StatementCollector(model);
        p.setRDFHandler(collector);
        for (InputStream is : inputStreams) {
            try {
                p.parse(is, "");
            } catch (IOException e) {
                throw new VocabularyImportException("Unable to parse data for import.");
            }
        }
    }

    private IRI resolveGlossaryIriFromImportedData(final Model model) {
        final Model glossaryRes = model.filter(null, RDF.TYPE, SKOS.CONCEPT_SCHEME);
        if (glossaryRes.size() == 1) {
            final Resource glossary = glossaryRes.iterator().next().getSubject();
            if (glossary.isIRI()) {
                return (IRI) glossary;
            } else {
                throw new VocabularyImportException(
                    "Blank node skos:ConceptScheme not supported.");
            }
        } else {
            throw new VocabularyImportException(
                "No unique skos:ConceptScheme found in the provided data.");
        }
    }

    private String resolveVocabularyIriFromImportedData() {
        return Utils
            .getVocabularyIri(
                model.filter(null, RDF.TYPE, SKOS.CONCEPT)
                    .stream()
                    .map(s -> s.getSubject().stringValue()).collect(Collectors.toSet()),
                config.getNamespace().getTerm().getSeparator());
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
                model.add(glossaryIri, SKOS.HAS_TOP_CONCEPT, t);
            }
        });
    }

    private void addDataIntoRepository(URI vocabularyIri) {
        final Repository repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        try (final RepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            final IRI targetContext = repository.getValueFactory().createIRI(vocabularyIri.toString());
            LOG.debug("Importing vocabulary into context <{}>.", targetContext);
            conn.add(model, targetContext);
            conn.commit();
        }
    }

    private Resource getGlossaryUri() {
        Set<Resource> glossaries = model.filter(null, RDF.TYPE, SKOS.CONCEPT_SCHEME).subjects();
        assert !glossaries.isEmpty() && glossaries.size() == 1;
        return glossaries.iterator().next();
    }

    private void setVocabularyLabelFromGlossary(final Vocabulary vocabulary) {
        final Set<Statement> labels = model.filter(getGlossaryUri(), DCTERMS.TITLE, null);
        labels.stream().filter(s -> {
            assert s.getObject() instanceof Literal;
            return Objects.equals(config.getPersistence().getLanguage(),
                ((Literal) s.getObject()).getLanguage().orElse(config.getPersistence().getLanguage()));
        }).findAny().ifPresent(s -> vocabulary.setLabel(s.getObject().stringValue()));
    }

    private Vocabulary createVocabulary(boolean rename, final URI vocabularyIri, final String vocabularyIriFromData) {
        URI newVocabularyIri;
        final String newVocabularyIriBase = vocabularyIriFromData;
        if ( vocabularyIri == null ) {
            newVocabularyIri = URI.create(getFreshVocabularyIri(rename, newVocabularyIriBase));
        } else {
            newVocabularyIri = vocabularyIri;
            if ( rename ) {
                Utils.changeNamespace(newVocabularyIriBase, newVocabularyIri.toString(), model);
            }
        }
        final Vocabulary vocabulary = new Vocabulary();
        vocabulary.setUri(newVocabularyIri);

        String newGlossaryIri = getFreshGlossaryIri(rename);
        final Glossary glossary = new Glossary();
        glossary.setUri(URI.create(newGlossaryIri));
        vocabulary.setGlossary(glossary);
        vocabulary.setModel(new cz.cvut.kbss.termit.model.Model());
        setVocabularyLabelFromGlossary(vocabulary);
        return vocabulary;
    }

    private String getFreshVocabularyIri(final boolean rename, final String newVocabularyIriBase) {
        String newVocabularyIri = newVocabularyIriBase;
        if (rename) {
            newVocabularyIri = getUniqueIriFromBase(newVocabularyIriBase, (r) -> vocabularyDao.find(URI.create(r)));
            if (!newVocabularyIri.equals(newVocabularyIriBase)) {
                Utils.changeNamespace(newVocabularyIriBase, newVocabularyIri, model);
            }
        }

        return newVocabularyIri;
    }

    private String getFreshGlossaryIri(final boolean rename) {
        final String origGlossary = getGlossaryUri().toString();
        String newGlossaryIri = origGlossary;
        if (rename) {
            newGlossaryIri = getUniqueIriFromBase(origGlossary, (r) -> vocabularyDao.findGlossary(URI.create(r)));
            if (!newGlossaryIri.equals(origGlossary)) {
                Utils.changeIri(origGlossary, newGlossaryIri, model);
            }
        }

        return newGlossaryIri;
    }
}
