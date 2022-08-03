package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.net.URI;
import java.util.*;

@Component
@SessionScope
public class EditableVocabularies implements Serializable {

    private final boolean allVocabulariesEditable;

    /**
     * Mapping of editable vocabulary identifiers to contexts in which the editable version is stored.
     */
    private final Map<URI, URI> editableVocabularies = new HashMap<>();

    public EditableVocabularies(Configuration configuration) {
        this.allVocabulariesEditable = configuration.getWorkspace().isAllVocabulariesEditable();
    }

    /**
     * Registers an editable copy of the vocabulary with the specified identifier to the specified repository context.
     *
     * @param vocabularyUri Vocabulary identifier
     * @param contextUri    Identifier of the context in which the editable data are
     */
    public void registerEditableVocabulary(URI vocabularyUri, URI contextUri) {
        Objects.requireNonNull(vocabularyUri);
        Objects.requireNonNull(contextUri);
        editableVocabularies.put(vocabularyUri, contextUri);
    }

    /**
     * Clears the registered contexts.
     */
    public void clear() {
        editableVocabularies.clear();
    }

    public boolean isEditable(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return allVocabulariesEditable || editableVocabularies.containsKey(vocabulary.getUri());
    }

    public Optional<URI> getVocabularyContext(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return Optional.ofNullable(editableVocabularies.get(vocabulary.getUri()));
    }

    /**
     * Gets a set of contexts containing the registered editable vocabularies.
     *
     * @return Set of context identifiers
     */
    public Set<URI> getRegisteredContexts() {
        return new HashSet<>(editableVocabularies.values());
    }
}
