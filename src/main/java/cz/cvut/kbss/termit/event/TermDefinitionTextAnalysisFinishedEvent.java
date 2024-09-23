package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.AbstractTerm;
import jakarta.annotation.Nonnull;

import java.net.URI;

/**
 * Indicates that a text analysis of a term definition was finished
 */
public class TermDefinitionTextAnalysisFinishedEvent extends VocabularyEvent {
    private final URI termUri;

    public TermDefinitionTextAnalysisFinishedEvent(@Nonnull Object source, @Nonnull AbstractTerm term) {
        super(source, term.getVocabulary());
        this.termUri = term.getUri();
    }

    public URI getTermUri() {
        return termUri;
    }
}
