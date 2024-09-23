package cz.cvut.kbss.termit.event;

import jakarta.annotation.Nonnull;

import java.net.URI;

/**
 * Indicates that a Vocabulary will be removed
 */
public class VocabularyWillBeRemovedEvent extends VocabularyEvent {

    public VocabularyWillBeRemovedEvent(@Nonnull Object source, @Nonnull URI vocabularyIri) {
        super(source, vocabularyIri);
    }
}
