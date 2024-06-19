package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

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
    public Vocabulary importVocabulary(@NonNull ImportConfiguration config, @NonNull ImportInput data) {
        if (SKOSImporter.supportsMediaType(data.mediaType())) {
            return getSkosImporter().importVocabulary(config, data);
        }
        if (ExcelImporter.supportsMediaType(data.mediaType())) {
            return getExcelImporter().importVocabulary(config, data);
        }
        throw new UnsupportedImportMediaTypeException(
                "Unsupported media type '" + data.mediaType() + "' for vocabulary import.");
    }

    private VocabularyImporter getSkosImporter() {
        return appContext.getBean(SKOSImporter.class);
    }

    private VocabularyImporter getExcelImporter() {
        return appContext.getBean(ExcelImporter.class);
    }
}
