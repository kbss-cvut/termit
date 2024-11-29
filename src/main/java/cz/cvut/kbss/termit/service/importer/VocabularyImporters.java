package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import cz.cvut.kbss.termit.service.importer.excel.ExcelImporter;
import jakarta.annotation.Nonnull;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Ensures correct importer is invoked for provided media types.
 */
@Component
public class VocabularyImporters implements VocabularyImporter {

    private final ApplicationContext appContext;

    public VocabularyImporters(ApplicationContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public Vocabulary importVocabulary(@Nonnull ImportConfiguration config, @Nonnull ImportInput data) {
        return resolveImporter(data.mediaType()).importVocabulary(config, data);
    }

    private VocabularyImporter resolveImporter(String mediaType) {
        if (SKOSImporter.supportsMediaType(mediaType)) {
            return getSkosImporter();
        } else if (ExcelImporter.supportsMediaType(mediaType)) {
            return getExcelImporter();
        }
        throw new UnsupportedImportMediaTypeException(
                "Unsupported media type '" + mediaType + "' for vocabulary import.");
    }

    @Override
    public Vocabulary importTermTranslations(@Nonnull URI vocabularyIri, @Nonnull ImportInput data) {
        return resolveImporter(data.mediaType()).importTermTranslations(vocabularyIri, data);
    }

    private VocabularyImporter getSkosImporter() {
        return appContext.getBean(SKOSImporter.class);
    }

    private VocabularyImporter getExcelImporter() {
        return appContext.getBean(ExcelImporter.class);
    }
}
