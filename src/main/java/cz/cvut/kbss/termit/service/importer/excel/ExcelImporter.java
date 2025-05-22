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
package cz.cvut.kbss.termit.service.importer.excel;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.importing.VocabularyDoesNotExistException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.dao.util.Quad;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.export.ExcelVocabularyExporter;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Imports a vocabulary from an Excel file.
 * <p>
 * It is expected that the file has a form matching either the downloadable template file or the file exported by TermIt
 * itself.
 * <p>
 * The importer processes all sheets in the workbook, skipping any sheets that do not match the expected format. The
 * format is given by column labels available currently in Czech and English (all other languages should use English
 * column labels). It is expected that each sheet contains the same terms in the same order. Sheet name should
 * correspond to language in English. Term identifiers may be specified in the sheet, but if they do not correspond to
 * the target vocabulary, they will be adjusted.
 * <p>
 * The importer removes any existing terms that appear in the sheet and would thus be overwritten.
 * <p>
 * SKOS relationships can be used in the sheet. If they are within a single vocabulary, terms may be referenced by their
 * labels. Relationships to external terms must use full URIs. State and type are resolved via label or identifier, both
 * methods are supported. Also, prefixed URIs are supported, as long as the workbook contains a sheet with prefix
 * definitions.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExcelImporter implements VocabularyImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelImporter.class);

    /**
     * Media type for legacy .xls files.
     */
    private static final String XLS_MEDIA_TYPE = "application/vnd.ms-excel";

    private final VocabularyDao vocabularyDao;

    private final TermRepositoryService termService;
    private final DataDao dataDao;

    private final LanguageService languageService;

    private final IdentifierResolver idResolver;
    private final Configuration config;

    private final EntityManager em;

    public ExcelImporter(VocabularyDao vocabularyDao, TermRepositoryService termService, DataDao dataDao,
                         LanguageService languageService, IdentifierResolver idResolver, Configuration config,
                         EntityManager em) {
        this.vocabularyDao = vocabularyDao;
        this.termService = termService;
        this.dataDao = dataDao;
        this.languageService = languageService;
        this.idResolver = idResolver;
        this.config = config;
        this.em = em;
    }

    @Override
    public Vocabulary importVocabulary(@Nonnull ImportConfiguration config, @Nonnull ImportInput data) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(data);
        if (config.vocabularyIri() == null || !vocabularyDao.exists(config.vocabularyIri())) {
            throw new VocabularyDoesNotExistException("An existing vocabulary must be specified for Excel import.");
        }
        final Vocabulary targetVocabulary = vocabularyDao.find(config.vocabularyIri()).orElseThrow(
                () -> NotFoundException.create(Vocabulary.class, config.vocabularyIri()));
        LOG.debug("Importing terms from Excel into vocabulary {}.", targetVocabulary);
        try {
            List<Term> terms = Collections.emptyList();
            Set<TermRelationship> rawDataToInsert = new HashSet<>();
            for (InputStream input : data.data()) {
                final Workbook workbook = new XSSFWorkbook(input);
                assert workbook.getNumberOfSheets() > 0;
                PrefixMap prefixMap = resolvePrefixMap(workbook);
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    final Sheet sheet = workbook.getSheetAt(i);
                    if (ExcelVocabularyExporter.PREFIX_SHEET_NAME.equals(sheet.getSheetName())) {
                        // Skip already processed prefix sheet
                        continue;
                    }
                    final LocalizedSheetImporter sheetImporter = new LocalizedSheetImporter(
                            new LocalizedSheetImporter.Services(termService, languageService),
                            prefixMap, terms);
                    terms = sheetImporter.resolveTermsFromSheet(sheet);
                    rawDataToInsert.addAll(sheetImporter.getRawDataToInsert());
                }
                prepareTermsForPersist(terms, targetVocabulary);
                persistNewTerms(terms, targetVocabulary, rawDataToInsert);
            }
        } catch (IOException e) {
            throw new VocabularyImportException("Unable to read input as Excel.", e);
        }
        return targetVocabulary;
    }

    private PrefixMap resolvePrefixMap(Workbook excel) {
        final Sheet prefixSheet = excel.getSheet(ExcelVocabularyExporter.PREFIX_SHEET_NAME);
        if (prefixSheet == null) {
            return new PrefixMap();
        } else {
            LOG.debug("Loading prefix map from sheet '{}'.", ExcelVocabularyExporter.PREFIX_SHEET_NAME);
            return new PrefixMap(prefixSheet);
        }
    }

    /**
     * Resolves term identifier w.r.t. the target vocabulary.
     * <p>
     * If the term does not have an identifier, it is generated so that existing instance can be removed before
     * inserting the imported term. If the term has an identifier, but it does not match the expected vocabulary-based
     * namespace, it is adjusted so that it does. Otherwise, the identifier is used.
     *
     * @param term       The imported term
     * @param vocabulary Vocabulary into which the term will be added
     * @return Term identifier
     */
    private URI resolveTermIdentifierWrtVocabulary(Term term, Vocabulary vocabulary) {
        final String termNamespace = resolveVocabularyTermNamespace(vocabulary);
        if (term.getUri() == null) {
            return idResolver.generateDerivedIdentifier(vocabulary.getUri(),
                                                        config.getNamespace().getTerm().getSeparator(),
                                                        term.getLabel().get(config.getPersistence().getLanguage()));
        }
        if (term.getUri() != null && !term.getUri().toString().startsWith(termNamespace)) {
            LOG.trace(
                    "Existing term identifier {} does not correspond to the expected vocabulary term namespace {}. Adjusting the term id.",
                    Utils.uriToString(term.getUri()), termNamespace);
            return idResolver.generateDerivedIdentifier(vocabulary.getUri(),
                                                        config.getNamespace().getTerm().getSeparator(),
                                                        term.getLabel().get(config.getPersistence().getLanguage()));
        }
        return term.getUri();
    }

    /**
     * Resolves namespace for identifiers of terms in the specified vocabulary.
     * <p>
     * It uses the vocabulary identifier and the configured term namespace separator.
     *
     * @param vocabulary Vocabulary whose term identifier namespace to resolve
     * @return Resolved namespace
     */
    private String resolveVocabularyTermNamespace(Vocabulary vocabulary) {
        return idResolver.buildNamespace(vocabulary.getUri().toString(),
                                         config.getNamespace().getTerm().getSeparator());
    }

    /**
     * Prepares terms for persist by:
     * <ul>
     *     <li>Resolving their identifiers and harmonizing them with vocabulary namespace</li>
     *     <li>Removing possibly pre-existing terms</li>
     * </ul>
     *
     * @param terms            Terms to process
     * @param targetVocabulary Target vocabulary
     */
    private void prepareTermsForPersist(List<Term> terms, Vocabulary targetVocabulary) {
        terms.stream().peek(t -> t.setUri(resolveTermIdentifierWrtVocabulary(t, targetVocabulary)))
             .peek(t -> t.getLabel().getValue().forEach((lang, value) -> {
                 final Optional<URI> existingUri = termService.findIdentifierByLabel(value,
                                                                                     targetVocabulary,
                                                                                     lang);
                 if (existingUri.isPresent() && !existingUri.get().equals(t.getUri())) {
                     throw new VocabularyImportException(
                             "Vocabulary already contains a term with label '" + value + "' with a different identifier than the imported one.",
                             "error.vocabulary.import.excel.labelWithDifferentIdentifierExists")
                             .addParameter("label", value)
                             .addParameter("existingUri", Utils.uriToString(existingUri.get()));
                 }
             }))
             .filter(t -> termService.exists(t.getUri())).forEach(t -> {
                 LOG.trace("Term {} already exists. Removing old version.", t);
                 termService.forceRemove(termService.findRequired(t.getUri()));
                 // Flush changes to prevent EntityExistsExceptions when term is already managed in PC as different type (Term vs TermInfo)
                 em.flush();
             });
    }

    private void persistNewTerms(List<Term> terms, Vocabulary targetVocabulary, Set<TermRelationship> rawDataToInsert) {
        // Ensure all parents are saved before we start adding children
        terms.stream().filter(t -> Utils.emptyIfNull(t.getParentTerms()).isEmpty())
             .forEach(root -> {
                 LOG.trace("Persisting root term {}.", root);
                 termService.addRootTermToVocabulary(root, targetVocabulary);
                 root.setVocabulary(targetVocabulary.getUri());
             });
        terms.stream().filter(t -> !Utils.emptyIfNull(t.getParentTerms()).isEmpty())
             .forEach(t -> {
                 t.setVocabulary(targetVocabulary.getUri());
                 LOG.trace("Persisting child term {}.", t);
                 termService.addChildTerm(t, t.getParentTerms().iterator().next());
             });
        // Insert term relationships as raw data because of possible object conflicts in the persistence context -
        // the same term being as multiple types (Term, TermInfo) in the same persistence context
        dataDao.insertRawData(rawDataToInsert.stream().map(tr -> new Quad(tr.subject().getUri(), tr.property(),
                                                                          tr.object().getUri(),
                                                                          targetVocabulary.getUri())).toList());
    }

    @Override
    public Vocabulary importTermTranslations(@Nonnull URI vocabularyIri, @Nonnull ImportInput data) {
        Objects.requireNonNull(vocabularyIri);
        Objects.requireNonNull(data);
        final Vocabulary targetVocabulary = vocabularyDao.find(vocabularyIri).orElseThrow(
                () -> NotFoundException.create(Vocabulary.class, vocabularyIri));
        LOG.debug("Importing translations for terms in vocabulary {}.", vocabularyIri);
        try {
            final List<Term> terms = readTermsFromSheet(data);
            terms.forEach(t -> {
                identifyTermByLabelIfNecessary(t, targetVocabulary);
                final Optional<Term> existingTerm = termService.find(t.getUri());
                if (existingTerm.isEmpty() || !existingTerm.get().getVocabulary().equals(vocabularyIri)) {
                    LOG.warn(
                            "Term with identifier '{}' not found in vocabulary '{}'. Skipping record resolved from Excel file.",
                            t.getUri(), vocabularyIri);
                    return;
                }
                mergeTranslations(t, existingTerm.get());
                termService.update(existingTerm.get());
                // Flush changes to prevent EntityExistsExceptions when term is already managed in PC as different type (Term vs TermInfo)
                em.flush();
            });
        } catch (IOException e) {
            throw new VocabularyImportException("Unable to read input as Excel.", e);
        }
        return targetVocabulary;
    }

    private void identifyTermByLabelIfNecessary(Term t, Vocabulary targetVocabulary) {
        if (t.getUri() == null) {
            final String termLabel = t.getLabel().get(config.getPersistence().getLanguage());
            if (termLabel == null) {
                throw new VocabularyImportException(
                        "Unable to identify terms in Excel - it contains neither term identifiers nor labels in primary language.",
                        "error.vocabulary.import.excel.missingIdentifierOrLabel");
            }
            t.setUri(idResolver.generateDerivedIdentifier(targetVocabulary.getUri(),
                                                          config.getNamespace().getTerm().getSeparator(),
                                                          termLabel));
        }
    }

    private List<Term> readTermsFromSheet(@Nonnull ImportInput data) throws IOException {
        List<Term> terms = Collections.emptyList();
        for (InputStream input : data.data()) {
            final Workbook workbook = new XSSFWorkbook(input);
            assert workbook.getNumberOfSheets() > 0;
            PrefixMap prefixMap = resolvePrefixMap(workbook);
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                final Sheet sheet = workbook.getSheetAt(i);
                if (ExcelVocabularyExporter.PREFIX_SHEET_NAME.equals(sheet.getSheetName())) {
                    // Skip already processed prefix sheet
                    continue;
                }
                final LocalizedSheetImporter sheetImporter = new LocalizedSheetImporter(
                        new LocalizedSheetImporter.Services(termService, languageService),
                        prefixMap, terms);
                terms = sheetImporter.resolveTermsFromSheet(sheet);
            }
        }
        return terms;
    }

    private void mergeTranslations(Term source, Term target) {
        target.setLabel(mergeSingularTranslations(source.getLabel(), target.getLabel()));
        target.setDefinition(mergeSingularTranslations(source.getDefinition(), target.getDefinition()));
        target.setDescription(mergeSingularTranslations(source.getDescription(), target.getDescription()));
        assert target.getAltLabels() != null;
        mergePluralTranslations(source.getAltLabels(), target.getAltLabels());
        assert target.getHiddenLabels() != null;
        mergePluralTranslations(source.getHiddenLabels(), target.getHiddenLabels());
        assert target.getExamples() != null;
        mergePluralTranslations(source.getExamples(), target.getExamples());
    }

    private MultilingualString mergeSingularTranslations(MultilingualString source, MultilingualString target) {
        if (target == null) {
            return source;
        }
        if (source == null) {
            return target;
        }
        source.getValue().forEach((lang, value) -> {
            if (!target.contains(lang)) {
                target.set(lang, value);
            }
        });
        return target;
    }

    private void mergePluralTranslations(Set<MultilingualString> source, Set<MultilingualString> target) {
        if (Utils.emptyIfNull(source).isEmpty()) {
            return;
        }
        // Remove just the existing language values
        target.forEach(t -> t.getLanguages().forEach(lang -> source.forEach(mls -> mls.remove(lang))));
        // Add the remainder
        target.addAll(source.stream().filter(mls -> !mls.isEmpty()).toList());
    }

    /**
     * Checks whether this importer supports the specified media type.
     *
     * @param mediaType Media type to check
     * @return {@code true} when media type is supported, {@code false} otherwise
     */
    public static boolean supportsMediaType(@Nonnull String mediaType) {
        return Constants.MediaType.EXCEL.equals(mediaType) || XLS_MEDIA_TYPE.equals(mediaType);
    }

    /**
     * Relationship between two terms.
     * <p>
     * Cannot use {@link Quad} directly because that the moment we are resolving the term relationships, the terms do
     * not have identifiers assigned yet.
     *
     * @param subject  Subject term
     * @param property Relationships property
     * @param object   Object term
     */
    record TermRelationship(Term subject, URI property, Term object) {
    }
}
