package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.resource.File;
import org.springframework.lang.NonNull;

import java.net.URI;

/**
 * Indicates that text analysis of a file was finished
 */
public class FileTextAnalysisFinishedEvent extends VocabularyEvent {

    private final URI fileUri;

    public FileTextAnalysisFinishedEvent(Object source, @NonNull File file) {
        super(source, file.getDocument().getVocabulary());
        this.fileUri = file.getUri();
    }

    public URI getFileUri() {
        return fileUri;
    }
}
