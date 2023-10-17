package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.net.URI;
import java.util.*;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

/**
 * Provides access to editable vocabularies.
 * <p>
 * Based on configuration, all vocabularies in a repository may be editable, or just a subset of them opened for
 * editing. When only a subset is open for editing, it assumed that it represents working copies of canonical versions
 * of the vocabularies.
 * <p>
 * This bean then allows checking whether a vocabulary is editable and what repository context it occupies (important
 * especially for the working copy scenario).
 */
@Component
public class EditableVocabularies implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(EditableVocabularies.class);

    private static final EditableVocabulariesHolder SHARED = new EditableVocabulariesHolder.SharedEditableVocabulariesHolder();

    private final boolean allVocabulariesEditable;

    private final ObjectProvider<EditableVocabulariesHolder> editableVocabularies;

    public EditableVocabularies(Configuration configuration,
                                ObjectProvider<EditableVocabulariesHolder> editableVocabularies) {
        this.allVocabulariesEditable = configuration.getWorkspace().allVocabulariesEditable();
        this.editableVocabularies = editableVocabularies;
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
        LOG.debug("Registering working context {} for vocabulary {}.", uriToString(contextUri),
                  uriToString(vocabularyUri));
        editableVocabularies.getObject().registerEditableVocabulary(vocabularyUri, contextUri);
    }

    /**
     * Clears the registered contexts.
     */
    public void clear() {
        editableVocabularies.ifAvailable(EditableVocabulariesHolder::clear);
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
        return allVocabulariesEditable || editableVocabularies.getIfAvailable(() -> SHARED)
                                                              .hasRegisteredVocabulary(vocabularyUri);
    }

    public Optional<URI> getVocabularyContext(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return getVocabularyContext(vocabulary.getUri());
    }

    public Optional<URI> getVocabularyContext(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return editableVocabularies.getIfAvailable(() -> SHARED).getVocabularyContext(vocabularyUri);
    }

    /**
     * Gets a set of contexts containing the registered editable vocabularies.
     *
     * @return Set of context identifiers
     */
    public Set<URI> getRegisteredContexts() {
        return editableVocabularies.getIfAvailable(() -> SHARED).getRegisteredContexts();
    }
}
