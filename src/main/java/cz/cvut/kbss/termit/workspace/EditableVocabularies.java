package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.net.URI;
import java.util.*;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

@Component
@SessionScope
public class EditableVocabularies implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(EditableVocabularies.class);

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
        LOG.debug("Registering working context {} for vocabulary {}.", uriToString(contextUri), uriToString(vocabularyUri));
        editableVocabularies.put(vocabularyUri, contextUri);
    }

    /**
     * Clears the registered contexts.
     */
    public void clear() {
        editableVocabularies.clear();
    }

    /**
     * Checks whether the specified vocabulary is editable.
     * <p>
     * A vocabulary is editable either if all vocabularies are editable (configured via {@link
     * cz.cvut.kbss.termit.util.Configuration.Workspace}) or when this instance contains a reference to a context
     * containing a working copy of the specified vocabulary.
     *
     * @param vocabulary Vocabulary to check
     * @return {@code true} when vocabulary is editable, {@code false otherwise}
     */
    public boolean isEditable(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return isEditable(vocabulary.getUri());
    }

    public boolean isEditable(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return allVocabulariesEditable || editableVocabularies.containsKey(vocabularyUri);
    }

    public Optional<URI> getVocabularyContext(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return getVocabularyContext(vocabulary.getUri());
    }

    public Optional<URI> getVocabularyContext(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return Optional.ofNullable(editableVocabularies.get(vocabularyUri));
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
