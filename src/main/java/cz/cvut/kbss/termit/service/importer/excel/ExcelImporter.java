package cz.cvut.kbss.termit.service.importer.excel;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.importing.VocabularyDoesNotExistException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExcelImporter implements VocabularyImporter {

    /**
     * Media type for legacy .xls files.
     */
    private static final String XLS_MEDIA_TYPE = "application/vnd.ms-excel";

    private final VocabularyDao vocabularyDao;

    private final TermRepositoryService termService;

    public ExcelImporter(VocabularyDao vocabularyDao, TermRepositoryService termService) {
        this.vocabularyDao = vocabularyDao;
        this.termService = termService;
    }

    @Override
    public Vocabulary importVocabulary(ImportConfiguration config, ImportInput data) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(data);
        if (config.vocabularyIri() == null || !vocabularyDao.exists(config.vocabularyIri())) {
            throw new VocabularyDoesNotExistException("An existing vocabulary must be specified for Excel import.");
        }
        final Vocabulary targetVocabulary = vocabularyDao.find(config.vocabularyIri()).orElseThrow(() -> NotFoundException.create(Vocabulary.class, config.vocabularyIri()));
        try {
            List<Term> terms = Collections.emptyList();
            for (InputStream input : data.data()) {
                final Workbook workbook = new XSSFWorkbook(input);
                assert workbook.getNumberOfSheets() > 0;
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    final Sheet sheet = workbook.getSheetAt(i);
                    terms = new LocalizedSheetImporter(terms).resolveTermsFromSheet(sheet);
                }
                // Ensure all parents are saved before we start adding children
                terms.stream().filter(t -> Utils.emptyIfNull(t.getParentTerms()).isEmpty()).forEach(root -> termService.addRootTermToVocabulary(root, targetVocabulary));
                terms.stream().filter(t -> !Utils.emptyIfNull(t.getParentTerms()).isEmpty()).forEach(t -> termService.addChildTerm(t, t.getParentTerms().iterator().next()));
            }
        } catch (IOException e) {
            throw new VocabularyImportException("Unable to read input as Excel.", e);
        }
        return targetVocabulary;
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
}
