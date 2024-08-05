package cz.cvut.kbss.termit.service.importer.excel;

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
import java.util.Set;

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

    private final IdentifierResolver idResolver;
    private final Configuration config;

    public ExcelImporter(VocabularyDao vocabularyDao, TermRepositoryService termService, DataDao dataDao,
                         IdentifierResolver idResolver, Configuration config) {
        this.vocabularyDao = vocabularyDao;
        this.termService = termService;
        this.dataDao = dataDao;
        this.idResolver = idResolver;
        this.config = config;
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
        final String termNamespace = resolveVocabularyTermNamespace(targetVocabulary);
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
                    final LocalizedSheetImporter sheetImporter = new LocalizedSheetImporter(prefixMap, terms,
                                                                                            idResolver, termNamespace);
                    terms = sheetImporter.resolveTermsFromSheet(sheet);
                    rawDataToInsert.addAll(sheetImporter.getRawDataToInsert());
                }
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
