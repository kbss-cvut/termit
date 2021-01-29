package cz.cvut.kbss.termit.persistence.dao.skos;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.DataImportException;
import cz.cvut.kbss.termit.exception.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.OWL;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SKOSImporter {

    private static final String VOCABULARY_TYPE = cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik;

    private static final Logger LOG = LoggerFactory.getLogger(SKOSImporter.class);

    private final SimpleDateFormat createdFormat = new SimpleDateFormat("yyyy-MM-dd");

    private final Configuration config;
    private final ChangeRecordDao changeRecordDao;
    private final SecurityUtils securityUtils;

    private final Repository repository;
    private final ValueFactory vf;

    private final Model model = new LinkedHashModel();

    private String vocabularyIri;

    @Autowired
    public SKOSImporter(Configuration config, ChangeRecordDao changeRecordDao, SecurityUtils securityUtils,
                        EntityManager em) {
        this.config = config;
        this.changeRecordDao = changeRecordDao;
        this.securityUtils = securityUtils;
        this.repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        vf = repository.getValueFactory();
    }

    public Vocabulary importVocabulary(String mediaType, InputStream... inputStreams) {
        if (inputStreams.length == 0) {
            throw new IllegalArgumentException("No input provided for importing vocabulary.");
        }
        LOG.debug("Vocabulary import started.");
        parseDataFromStreams(mediaType, inputStreams);
        resolveVocabularyIri();
        LOG.trace("Vocabulary identifier resolved to {}.", vocabularyIri);
        insertTermVocabularyMembership();
        insertTopConceptAssertions();
        addDataIntoRepository();
        generatePersistChangeRecord();
        LOG.debug("Vocabulary import successfully finished.");
        return constructVocabularyInstance();
    }

    private void parseDataFromStreams(String mediaType, InputStream... inputStreams) {
        final RDFFormat rdfFormat = Rio.getParserFormatForMIMEType(mediaType).orElseThrow(
                () -> new UnsupportedImportMediaTypeException("Media type" + mediaType + "not supported."));
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

    private void addDataIntoRepository() {
        try (final RepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            final String targetContext = vocabularyIri;
            LOG.debug("Importing vocabulary into context <{}>.", targetContext);
            conn.add(model, vf.createIRI(targetContext));
            conn.commit();
        }
    }

    private void resolveVocabularyIri() {
        final Model resVocabulary = model.filter(null, RDF.TYPE, vf.createIRI(VOCABULARY_TYPE));
        if (resVocabulary.size() == 1) {
            this.vocabularyIri = resVocabulary.iterator().next().getSubject().stringValue();
            return;
        }
        final Model res = model.filter(null, RDF.TYPE, OWL.ONTOLOGY);
        if (res.size() == 1) {
            this.vocabularyIri = res.iterator().next().getSubject().stringValue();
            return;
        }
        throw new IllegalArgumentException(
                "No vocabulary or ontology found in the provided data. This means target storage context cannot be determined.");
    }

    private Vocabulary constructVocabularyInstance() {
        final Vocabulary instance = new Vocabulary();
        instance.setUri(URI.create(vocabularyIri));
        final Set<Statement> labels = model.filter(vf.createIRI(vocabularyIri), DCTERMS.TITLE, null);
        labels.stream().filter(s -> {
            assert s.getObject() instanceof Literal;
            return Objects.equals(config.get(ConfigParam.LANGUAGE),
                    ((Literal) s.getObject()).getLanguage().orElse(config.get(ConfigParam.LANGUAGE)));
        }).findAny().ifPresent(s -> instance.setLabel(s.getObject().stringValue()));
        return instance;
    }

    private void insertTermVocabularyMembership() {
        LOG.trace("Generating vocabulary membership statements for terms.");
        final IRI vocabularyId = vf.createIRI(vocabularyIri);
        model.addAll(model.filter(null, RDF.TYPE, SKOS.CONCEPT).stream()
                          .map(s -> vf.createStatement(s.getSubject(), vf.createIRI(
                                  cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku), vocabularyId))
                          .collect(Collectors.toList()));
    }

    private void insertTopConceptAssertions() {
        LOG.trace("Generating top concept assertions.");
        final IRI vocabularyId = vf.createIRI(vocabularyIri);
        final List<Value> glossary = model
                .filter(vocabularyId, vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar), null).stream()
                .map(Statement::getObject).collect(Collectors.toList());
        if (glossary.isEmpty()) {
            LOG.debug("No glossary found for imported vocabulary {}, top concepts will not be identified.",
                    vocabularyId);
            return;
        }
        assert glossary.size() == 1;
        final List<Resource> terms = model.filter(null, RDF.TYPE, SKOS.CONCEPT).stream().map(Statement::getSubject)
                                          .collect(Collectors.toList());
        terms.forEach(t -> {
            final List<Value> broader = model.filter(t, SKOS.BROADER, null).stream().map(Statement::getObject)
                                             .collect(Collectors.toList());
            final boolean hasBroader = broader.stream()
                                              .anyMatch(p -> model.contains((Resource) p, RDF.TYPE, SKOS.CONCEPT));
            final List<Value> narrower = model.filter(null, SKOS.NARROWER, t).stream().map(Statement::getObject)
                                              .collect(Collectors.toList());
            final boolean isNarrower = narrower.stream()
                                               .anyMatch(p -> model.contains((Resource) p, RDF.TYPE, SKOS.CONCEPT));
            if (!hasBroader && !isNarrower) {
                model.add((Resource) glossary.get(0), SKOS.HAS_TOP_CONCEPT, t);
            }
        });
    }

    private void generatePersistChangeRecord() {
        final List<Value> created = model.filter(vf.createIRI(vocabularyIri), DCTERMS.CREATED, null).stream()
                                         .map(Statement::getObject).collect(Collectors.toList());
        if (created.isEmpty()) {
            LOG.trace("No vocabulary creation date available.");
            return;
        }
        final Vocabulary asset = constructVocabularyInstance();
        try {
            final Instant timestamp = createdFormat.parse(created.get(0).stringValue()).toInstant();
            final AbstractChangeRecord changeRecord = new PersistChangeRecord(asset);
            changeRecord.setAuthor(securityUtils.getCurrentUser().toUser());
            changeRecord.setTimestamp(timestamp);
            LOG.debug("Saving persist record for imported vocabulary. {}", changeRecord);
            changeRecordDao.persist(changeRecord, asset);
        } catch (ParseException e) {
            LOG.warn("Unable to parse vocabulary creation date. Is it in ISO format?", e);
        }
    }

    /**
     * Guesses media type from the specified file name. E.g., if the file ends with ".ttl", the result will be {@link
     * cz.cvut.kbss.termit.util.Constants.Turtle#MEDIA_TYPE}.
     *
     * @param fileName File name used to guess media type
     * @return Guessed media type
     * @throws UnsupportedImportMediaTypeException If the media type could not be determined
     */
    public static String guessMediaType(String fileName) {
        return Rio.getParserFormatForFileName(fileName)
                  .orElseThrow(() -> new UnsupportedImportMediaTypeException("Unsupported type of file " + fileName))
                  .getDefaultMIMEType();
    }
}
