package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.AbstractTerm;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Indicates that a text analysis of a term definition was finished
 */
public class VocabularyTermDefinitionTextAnalysisFinishedEvent extends VocabularyEvent {
    private final URI termUri;

    public VocabularyTermDefinitionTextAnalysisFinishedEvent(Object source, @NotNull AbstractTerm term) {
        super(source, term.getVocabulary());
        this.termUri = term.getUri();
    }

    public URI getTermUri() {
        return termUri;
    }
}
