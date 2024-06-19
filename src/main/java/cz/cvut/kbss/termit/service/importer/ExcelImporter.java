package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExcelImporter implements VocabularyImporter {

    /**
     * Media type for legacy .xls files.
     */
    private static final String XLS_MEDIA_TYPE = "application/vnd.ms-excel";

    @Override
    public Vocabulary importVocabulary(ImportConfiguration config, ImportInput data) {
        // TODO
        return null;
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
