package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.resource.File;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Indicates that text analysis of a file was finished
 */
public class VocabularyFileTextAnalysisFinishedEvent extends VocabularyEvent {

    private final URI fileUri;

    public VocabularyFileTextAnalysisFinishedEvent(Object source, @NotNull File file) {
        super(source, file.getDocument().getVocabulary());
        this.fileUri = file.getUri();
    }

    public URI getFileUri() {
        return fileUri;
    }
}
