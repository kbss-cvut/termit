package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

/**
 * Represents an event of modification of the content of a vocabulary.
 * <p>
 * This typically means a term is added, removed or modified. Modification of vocabulary metadata themselves is not considered here.
 */
public class VocabularyContentModified extends ApplicationEvent {

    public VocabularyContentModified(Object source) {
        super(source);
    }
}
