package cz.cvut.kbss.termit.event;

import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Indicates that a Vocabulary will be removed
 */
public class VocabularyWillBeRemovedEvent extends VocabularyEvent {

    public VocabularyWillBeRemovedEvent(Object source, @NotNull URI vocabularyIri) {
        super(source, vocabularyIri);
    }
}
