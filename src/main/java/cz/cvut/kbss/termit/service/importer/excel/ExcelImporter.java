package cz.cvut.kbss.termit.service.importer.excel;

import cz.cvut.kbss.jopa.model.EntityManager;
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
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
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
    public Vocabulary importVocabulary(ImportConfiguration config, ImportInput data) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(data);
        if (config.vocabularyIri() == null || !vocabularyDao.exists(config.vocabularyIri())) {
            throw new VocabularyDoesNotExistException("An existing vocabulary must be specified for Excel import.");
        }
        final Vocabulary targetVocabulary = vocabularyDao.find(config.vocabularyIri()).orElseThrow(
                () -> NotFoundException.create(Vocabulary.class, config.vocabularyIri()));
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
                terms.stream().peek(t -> t.setUri(resolveTermIdentifier(targetVocabulary, t)))
                     .peek(t -> t.getLabel().getValue().forEach((lang, value) -> {
                         final Optional<URI> existingUri = termService.findIdentifierByLabel(value,
                                                                                             targetVocabulary,
                                                                                             lang);
                         if (existingUri.isPresent() && !existingUri.get().equals(t.getUri())) {
                             throw new VocabularyImportException(
                                     "Vocabulary already contains a term with label '" + value + "' with a different identifier than the imported one.");
                         }
                     }))
                     .filter(t -> termService.exists(t.getUri())).forEach(t -> {
                         LOG.trace("Term {} already exists. Removing old version.", t);
                         termService.forceRemove(termService.findRequired(t.getUri()));
                         // Flush changes to prevent EntityExistsExceptions when term is already managed in PC as different type (Term vs TermInfo)
                         em.flush();
                     });
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
     * Resolves term identifier.
     * <p>
     * If the term does not have an identifier, it is generated so that existing instance can be removed before
     * inserting the imported term. If the term has an identifier, but it does not match the expected vocabulary-based
     * namespace, it is adjusted so that it does. Otherwise, the identifier is used.
     *
     * @param vocabulary Vocabulary into which the term will be added
     * @param term       The imported term
     * @return Term identifier
     */
    private URI resolveTermIdentifier(Vocabulary vocabulary, Term term) {
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
     * Checks whether this importer supports the specified media type.
     *
     * @param mediaType Media type to check
     * @return {@code true} when media type is supported, {@code false} otherwise
     */
    public static boolean supportsMediaType(@NonNull String mediaType) {
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
