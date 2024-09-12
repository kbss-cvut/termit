package cz.cvut.kbss.termit.event;

import org.springframework.lang.NonNull;

import java.net.URI;

/**
 * Indicates that a Vocabulary will be removed
 */
public class VocabularyWillBeRemovedEvent extends VocabularyEvent {

    public VocabularyWillBeRemovedEvent(@NonNull Object source, @NonNull URI vocabularyIri) {
        super(source, vocabularyIri);
    }
}
