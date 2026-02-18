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
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.event.TermReferencesUpdatedEvent;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.importing.MissingLanguageTagException;
import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.exception.importing.VocabularyExistsException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotNull;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
public class SKOSImporter implements VocabularyImporter {

    private static final Logger LOG = LoggerFactory.getLogger(SKOSImporter.class);

    private static final Set<String> MULTILINGUAL_PROPERTIES = Set.of(
            SKOS.PREF_LABEL.toString(),
            SKOS.ALT_LABEL.toString(),
            SKOS.HIDDEN_LABEL.toString(),
            SKOS.SCOPE_NOTE.toString(),
            SKOS.DEFINITION.toString()
    );

    private static final Set<IRI> INFERABLE_MAPPING_PROPERTIES = Set.of(SKOS.EXACT_MATCH, SKOS.RELATED_MATCH);

    private final Configuration config;
    private final VocabularyDao vocabularyDao;
    private final VocabularyNamespaceResolver namespaceResolver;
    private final ApplicationEventPublisher eventPublisher;

    private final EntityManager em;

    private final Model model = new LinkedHashModel();
    private final Model mappingStatements = new LinkedHashModel();

    private final ValueFactory vf = SimpleValueFactory.getInstance();

    private String namespace;
    private IRI glossaryIri;

    @Autowired
    public SKOSImporter(Configuration config, VocabularyDao vocabularyDao,
                        VocabularyNamespaceResolver namespaceResolver, ApplicationEventPublisher eventPublisher,
                        EntityManager em) {
        this.config = config;
        this.vocabularyDao = vocabularyDao;
        this.namespaceResolver = namespaceResolver;
        this.eventPublisher = eventPublisher;
        this.em = em;
    }

    @Override
    public Vocabulary importVocabulary(@Nonnull ImportConfiguration config, @Nonnull ImportInput data) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(data);
        return importVocabulary(config.allowReIdentify(), config.vocabularyIri(), data.mediaType(), config.prePersist(),
                                data.data());
    }

    private Vocabulary importVocabulary(final boolean rename,
                                        final URI vocabularyIri,
                                        final String mediaType,
                                        final Consumer<Vocabulary> prePersist,
                                        final InputStream... inputStreams) {
        if (inputStreams.length == 0) {
            throw new IllegalArgumentException("No input provided for importing vocabulary.");
        }
        LOG.debug("Vocabulary import started.");
        parseDataFromStreams(mediaType, inputStreams);
        validateTermLabels();
        validateRequiredLanguageTags();

        this.glossaryIri = resolveGlossaryIriFromImportedData();
        this.namespace = resolveVocabularyNamespaceFromData();
        LOG.trace("Importing glossary {}.", glossaryIri);
        removeTopConceptOfAssertions();
        insertHasTopConceptAssertions();
        removeSelfReferences();

        final Optional<String> vocabularyIriFromData = resolveVocabularyIriFromImportedData();
        validateVocabularyIriCompatibility(vocabularyIri, vocabularyIriFromData);

        final Vocabulary vocabulary = createVocabulary(rename, vocabularyIri, vocabularyIriFromData);
        ensureConceptIrisAreCompatibleWithTermIt(vocabulary);
        extractSkosMappingStatements();

        if (vocabularyIri == null) {
            LOG.trace("New vocabulary {} with a new glossary {}.", vocabulary.getUri(),
                      vocabulary.getGlossary().getUri());
            ensureUniqueness(vocabulary);
        } else {
            clearVocabulary(vocabulary);
        }
        em.flush();
        em.clear();

        prePersist.accept(vocabulary);
        vocabularyDao.persist(vocabulary);
        addDataIntoRepository(vocabulary.getUri());
        notifyReferencingTerms();
        LOG.debug("Vocabulary import successfully finished.");
        return vocabulary;
    }

    private void validateTermLabels() {
        LOG.debug("Checking terms have labels.");
        model.stream().filter(s -> RDF.TYPE.equals(s.getPredicate()) && SKOS.CONCEPT.equals(s.getObject()))
             .forEach(s -> {
                 final Resource term = s.getSubject();
                 final Set<Statement> labels = model.filter(term, SKOS.PREF_LABEL, null);
                 if (labels.isEmpty()) {
                     final VocabularyImportException ex = new VocabularyImportException(
                             "Term " + term + " has no label.",
                             "error.vocabulary.import.skos.missingLabel");
                     ex.addParameter("term", term.stringValue());
                     throw ex;
                 }
             });
    }

    /**
     * Checks that all values of multilingual properties of all terms have a language tag.
     *
     * @throws MissingLanguageTagException if a value of a multilingual property is missing a language tag
     */
    private void validateRequiredLanguageTags() {
        LOG.debug("Checking that only language-tagged literals are provided.");
        model.stream()
             .filter(statement -> MULTILINGUAL_PROPERTIES.contains(statement.getPredicate().stringValue()))
             .filter(statement -> statement.getObject().isLiteral())
             .forEach(statement -> {
                 if (((Literal) statement.getObject()).getLanguage().isEmpty()) {
                     final MissingLanguageTagException ex = new MissingLanguageTagException(
                             "Missing required language tag in " + statement,
                             "error.vocabulary.import.skos.missingLanguageTag");
                     ex.addParameter("term", statement.getSubject().stringValue());
                     ex.addParameter("property", statement.getPredicate().stringValue());
                     throw ex;
                 }
             });
    }

    private static void validateVocabularyIriCompatibility(URI vocabularyIri, Optional<String> vocabularyIriFromData) {
        if (vocabularyIri != null && vocabularyIriFromData.isPresent() && !vocabularyIri.toString().equals(
                vocabularyIriFromData.get())) {
            throw new IllegalArgumentException(
                    "Cannot import a vocabulary into an existing one with different identifier.");
        }
    }

    private void ensureUniqueness(Vocabulary vocabulary) {
        if (vocabularyDao.exists(vocabulary.getUri())) {
            throw new VocabularyExistsException("The vocabulary IRI '" + vocabulary.getUri() + "' already exists.");
        }
        final Optional<Glossary> existingGlossary = vocabularyDao.findGlossary(vocabulary.getGlossary().getUri());
        if (existingGlossary.isPresent()) {
            throw new VocabularyExistsException("The glossary '" + vocabulary.getGlossary()
                                                                             .getUri() + "' already exists.");
        }
    }

    private void clearVocabulary(Vocabulary newVocabulary) {
        final Optional<Vocabulary> possibleVocabulary = vocabularyDao.find(newVocabulary.getUri());
        possibleVocabulary.ifPresent(toRemove -> {
            newVocabulary.setDocument(toRemove.getDocument());
            newVocabulary.setAcl(toRemove.getAcl());
            vocabularyDao.removeVocabularyKeepDocument(toRemove);
        });
    }

    private void ensureConceptIrisAreCompatibleWithTermIt(Vocabulary vocabulary) {
        assert vocabulary.getProperties()
                         .getOrDefault(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri, Set.of())
                         .size() == 1;
        final String ns = vocabulary.getProperties().get(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri)
                                    .iterator().next().toString();
        final char separator = ns.charAt(ns.length() - 1);
        final Statement[] statements = model.filter(null, RDF.TYPE, SKOS.CONCEPT).toArray(new Statement[]{});
        for (final Statement c : statements) {
            if (c.getSubject().stringValue().contains(ns)) {
                continue;
            }
            final String sIri = c.getSubject().stringValue();
            final int lastSeparator = sIri.lastIndexOf(separator);
            final String newIri = ns + sIri.substring(lastSeparator + 1);
            Utils.changeIri(c.getSubject().stringValue(), newIri, model);
        }
    }

    private void parseDataFromStreams(String mediaType, InputStream... inputStreams) {
        final RDFFormat rdfFormat = Rio.getParserFormatForMIMEType(mediaType).orElseThrow(
                () -> new UnsupportedImportMediaTypeException("Media type '" + mediaType + "' not supported."));
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

    private IRI resolveGlossaryIriFromImportedData() {
        final Set<Resource> glossaryRes = model.filter(null, RDF.TYPE, SKOS.CONCEPT_SCHEME).subjects();
        if (glossaryRes.size() == 1) {
            final Resource glossary = glossaryRes.iterator().next();
            if (glossary.isIRI()) {
                return (IRI) glossary;
            } else {
                throw new VocabularyImportException("Blank node skos:ConceptScheme not supported.");
            }
        } else {
            throw new VocabularyImportException("No unique skos:ConceptScheme found in the provided data.");
        }
    }

    private String resolveVocabularyNamespaceFromData() {
        final Optional<String> ns = model.filter(null,
                                                 vf.createIRI(
                                                         cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri),
                                                 null).stream().map(s -> s.getObject().stringValue())
                                         .findFirst();
        ns.ifPresent(s -> LOG.trace("Found explicit preferred namespace URI: {}", s));
        return IdentifierResolver.ensureNamespaceSeparatorTermination(ns.orElseGet(() -> {
            final String result = Utils.extractVocabularyNamespaceFromTermIris(
                    model.filter(null, RDF.TYPE, SKOS.CONCEPT)
                         .stream()
                         .map(s -> s.getSubject().stringValue()).collect(Collectors.toSet()));
            LOG.trace("Extracted namespace {} from term identifiers.", result);
            return result;
        }));
    }

    private Optional<String> resolveVocabularyIriFromImportedData() {
        final Optional<Resource> subject = model.filter(null, RDF.TYPE, vf.createIRI(
                cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik)).subjects().stream().findFirst();
        String iri = subject.map(Value::stringValue).orElse(namespace);
        if (iri.contains(config.getNamespace().getTerm().getSeparator())) {
            iri = iri.substring(0, iri.indexOf(config.getNamespace().getTerm().getSeparator()));
            return Optional.of(stripTrailingSeparator(iri));
        }
        return Optional.empty();
    }

    private static String stripTrailingSeparator(String str) {
        if (str.endsWith("/") || str.equals("#")) {
            return str.substring(0, str.length() - 1);
        }
        return str;
    }

    private void removeTopConceptOfAssertions() {
        LOG.trace("Removing explicit skos:topConceptOf statements.");
        model.remove(null, SKOS.TOP_CONCEPT_OF, null);
    }

    private void insertHasTopConceptAssertions() {
        LOG.trace("Generating skos:hasTopConcept assertions.");
        final List<Resource> terms = model.filter(null, RDF.TYPE, SKOS.CONCEPT)
                                          .stream().map(Statement::getSubject).toList();
        terms.forEach(t -> {
            final List<Value> broader = model.filter(t, SKOS.BROADER, null)
                                             .stream().map(Statement::getObject).toList();
            final boolean hasBroader = broader.stream()
                                              .anyMatch(p -> model.contains((Resource) p, RDF.TYPE, SKOS.CONCEPT));
            final List<Value> narrower = model.filter(null, SKOS.NARROWER, t)
                                              .stream().map(Statement::getObject).toList();
            final boolean isNarrower = narrower.stream()
                                               .anyMatch(p -> model.contains((Resource) p, RDF.TYPE, SKOS.CONCEPT));
            if (!hasBroader && !isNarrower) {
                model.add(glossaryIri, SKOS.HAS_TOP_CONCEPT, t);
            }
        });
    }

    private void removeSelfReferences() {
        LOG.trace("Removing self-referencing SKOS relationship statements.");
        final List<Resource> terms = model.filter(null, RDF.TYPE, SKOS.CONCEPT)
                                          .stream().map(Statement::getSubject).toList();
        terms.forEach(t -> {
            model.remove(t, SKOS.RELATED, t);
            model.remove(t, SKOS.EXACT_MATCH, t);
            model.remove(t, SKOS.RELATED_MATCH, t);
            model.remove(t, SKOS.BROADER, t);
        });
    }

    /**
     * Extracts SKOS mapping property statements from the imported model into a separate one for later processing.
     */
    private void extractSkosMappingStatements() {
        INFERABLE_MAPPING_PROPERTIES.stream()
                                    .flatMap(prop -> model.filter(null, prop, null).stream())
                                    .forEach(mappingStatements::add);
        model.removeAll(mappingStatements);
    }

    private void addDataIntoRepository(URI vocabularyIri) {
        final Repository repository = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        try (final RepositoryConnection conn = repository.getConnection()) {
            conn.begin();
            final IRI targetContext = repository.getValueFactory().createIRI(vocabularyIri.toString());
            LOG.debug("Importing vocabulary into context <{}>.", targetContext);
            conn.add(model, targetContext);
            addAssertedSkosMappingStatements(conn, targetContext);
            conn.commit();
        }
    }

    /**
     * Adds only those SKOS mapping property statements that cannot be inferred from existing data to the repository.
     */
    private void addAssertedSkosMappingStatements(RepositoryConnection conn, IRI targetContext) {
        for (Statement s : mappingStatements) {
            if (!conn.hasStatement(s, true)) {
                conn.add(s, targetContext);
            }
        }
    }

    private Vocabulary createVocabulary(boolean rename, URI vocabularyIri, Optional<String> vocabularyIriFromData) {
        URI newVocabularyIri;
        if (vocabularyIri == null) {
            newVocabularyIri = URI.create(getFreshVocabularyIri(rename, vocabularyIriFromData.orElse(namespace)));
        } else {
            assert vocabularyIriFromData.isEmpty() || vocabularyIri.toString().equals(vocabularyIriFromData.get());
            newVocabularyIri = vocabularyIri;
        }
        final Vocabulary vocabulary = new Vocabulary();
        vocabulary.setUri(newVocabularyIri);

        String newGlossaryIri = getFreshGlossaryIri(rename);
        final Glossary glossary = new Glossary();
        glossary.setUri(URI.create(newGlossaryIri));
        if (Objects.equals(vocabulary.getUri(), glossary.getUri())) {
            throw new VocabularyImportException("Vocabulary IRI cannot be equal to glossary IRI.",
                                                "error.vocabulary.import.skos.vocabularyIriEqualsGlossaryIri");
        }
        vocabulary.setGlossary(glossary);
        vocabulary.setModel(new cz.cvut.kbss.termit.model.Model());
        setVocabularyPrimaryLanguageFromGlossary(vocabulary);
        setVocabularyLabelFromGlossary(vocabulary);
        setVocabularyDescriptionFromGlossary(vocabulary);
        setVocabularyNamespaceInfoFromData(vocabulary);
        return vocabulary;
    }

    private void setVocabularyPrimaryLanguageFromGlossary(Vocabulary vocabulary) {
        boolean languageSet = handleGlossaryLiteralStringProperty(DCTERMS.LANGUAGE, vocabulary::setPrimaryLanguage);
        if (!languageSet) {
            AtomicReference<MultilingualString> labelRef = new AtomicReference<>();
            handleGlossaryStringProperty(DCTERMS.TITLE, labelRef::set, config.getPersistence().getLanguage());
            MultilingualString label = labelRef.get();
            if (label == null ||
                    label.contains(config.getPersistence().getLanguage())) {
                vocabulary.setPrimaryLanguage(config.getPersistence().getLanguage());
            } else {
                vocabulary.setPrimaryLanguage(label.getLanguages().iterator().next());
            }
        }
    }

    private String getFreshVocabularyIri(boolean rename, String newVocabularyIriBase) {
        String newVocabularyIri = newVocabularyIriBase;
        if (rename) {
            newVocabularyIri = getUniqueIriFromBase(newVocabularyIriBase, r -> vocabularyDao.find(URI.create(r)));
            if (!newVocabularyIri.equals(newVocabularyIriBase)) {
                Utils.changeNamespace(newVocabularyIriBase, newVocabularyIri, model);
            }
        }
        return newVocabularyIri;
    }

    private String getFreshGlossaryIri(final boolean rename) {
        this.glossaryIri = resolveGlossaryIriFromImportedData();
        final String origGlossary = glossaryIri.stringValue();
        String newGlossaryIri = origGlossary;
        if (rename) {
            newGlossaryIri = getUniqueIriFromBase(origGlossary, r -> vocabularyDao.findGlossary(URI.create(r)));
            if (!newGlossaryIri.equals(origGlossary)) {
                Utils.changeIri(origGlossary, newGlossaryIri, model);
                this.glossaryIri = SimpleValueFactory.getInstance().createIRI(newGlossaryIri);
            }
        }
        return newGlossaryIri;
    }

    private void setVocabularyLabelFromGlossary(final Vocabulary vocabulary) {
        handleGlossaryStringProperty(DCTERMS.TITLE, vocabulary::setLabel, vocabulary.getPrimaryLanguage());
    }

    /**
     * Looks up the specified property in the glossary and loads values as a multilingual string. If no property is
     * found, an empty multilingual string is passed to the consumer.
     *
     * @param property        Property to look up in the glossary
     * @param consumer        Consumer to accept the multilingual string
     * @param defaultLanguage The language to use when no language is specified in the string property
     */
    private void handleGlossaryStringProperty(IRI property, Consumer<MultilingualString> consumer,
                                              String defaultLanguage) {
        final Set<Statement> values = model.filter(glossaryIri, property, null);
        final MultilingualString mls = new MultilingualString();
        values.stream().filter(s -> s.getObject().isLiteral()).forEach(s -> {
            final Literal obj = (Literal) s.getObject();
            mls.set(obj.getLanguage().orElse(defaultLanguage), obj.getLabel());
        });
        consumer.accept(mls);
    }

    /**
     * Looks up the specified property in the glossary and loads the first value as a literal string. If no property is
     * found, the consumer is not called.
     *
     * @param property Property to look up in the glossary
     * @param consumer Consumer to accept the literal string value if found
     * @return true if the property was found and the consumer was called, false otherwise
     */
    private boolean handleGlossaryLiteralStringProperty(IRI property, Consumer<String> consumer) {
        final Set<Statement> values = model.filter(glossaryIri, property, null);
        return values.stream()
                     .filter(s -> s.getObject().isLiteral()).findFirst()
                     .map(s -> (Literal) s.getObject())
                     .map(Literal::getLabel)
                     .map(value -> {
                         consumer.accept(value);
                         return true;
                     })
                     .orElse(false);
    }

    private void setVocabularyDescriptionFromGlossary(final Vocabulary vocabulary) {
        handleGlossaryStringProperty(DCTERMS.DESCRIPTION, vocabulary::setDescription, vocabulary.getPrimaryLanguage());
    }

    private void setVocabularyNamespaceInfoFromData(Vocabulary vocabulary) {
        namespaceResolver.setVocabularyPreferredNamespace(vocabulary, namespace);
        final Model prefixModel = model.filter(null, vf.createIRI(
                cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespacePrefix), null);
        prefixModel.forEach(s -> {
            final String prefix = s.getObject().stringValue();
            LOG.trace("Found preferred namespace prefix: {}", prefix);
            vocabulary.addUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespacePrefix,
                                                prefix);
        });
    }

    private void notifyReferencingTerms() {
        mappingStatements.forEach(s -> {
            final String property = s.getPredicate().stringValue();
            final URI termUri = URI.create(s.getObject().stringValue());
            eventPublisher.publishEvent(new TermReferencesUpdatedEvent(this, termUri, property));
        });
        model.filter(null, SKOS.BROAD_MATCH, null).forEach(s -> {
            final URI termUri = URI.create(s.getObject().stringValue());
            eventPublisher.publishEvent(new TermReferencesUpdatedEvent(this, termUri, SKOS.NARROWER.stringValue()));
        });
    }

    @Override
    public Vocabulary importTermTranslations(@Nonnull URI vocabularyIri, @Nonnull ImportInput data) {
        throw new UnsupportedOperationException(
                "Importing term translations from SKOS file is currently not supported.");
    }

    /**
     * Checks whether this importer supports the specified media type.
     *
     * @param mediaType Media type to check
     * @return {@code true} when media type is supported, {@code false} otherwise
     */
    public static boolean supportsMediaType(@NotNull String mediaType) {
        return Rio.getParserFormatForMIMEType(mediaType).isPresent();
    }
}
