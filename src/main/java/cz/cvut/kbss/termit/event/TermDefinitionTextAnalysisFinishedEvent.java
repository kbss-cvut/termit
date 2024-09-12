package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.AbstractTerm;
import org.springframework.lang.NonNull;

import java.net.URI;

/**
 * Indicates that a text analysis of a term definition was finished
 */
public class TermDefinitionTextAnalysisFinishedEvent extends VocabularyEvent {
    private final URI termUri;

    public TermDefinitionTextAnalysisFinishedEvent(@NonNull Object source, @NonNull AbstractTerm term) {
        super(source, term.getVocabulary());
        this.termUri = term.getUri();
    }

    public URI getTermUri() {
        return termUri;
    }
}
