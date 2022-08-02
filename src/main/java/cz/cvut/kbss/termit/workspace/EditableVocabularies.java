package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@SessionScope
public class EditableVocabularies {

    private final Configuration.Workspace workspaceConfig;

    /**
     * Mapping of editable vocabulary identifiers to contexts in which the editable version is stored.
     */
    private final Map<URI, URI> editableVocabularies = new HashMap<>();

    public EditableVocabularies(Configuration configuration) {
        this.workspaceConfig = configuration.getWorkspace();
    }

    /**
     * Registers an editable copy of the vocabulary with the specified identifier to the specified repository context.
     * @param vocabularyUri Vocabulary identifier
     * @param contextUri Identifier of the context in which the editable data are
     */
    public void registerEditableVocabulary(URI vocabularyUri, URI contextUri) {
        Objects.requireNonNull(vocabularyUri);
        Objects.requireNonNull(contextUri);
        editableVocabularies.put(vocabularyUri, contextUri);
    }

    public boolean isEditable(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return editableVocabularies.containsKey(vocabulary.getUri()) || workspaceConfig.isAllVocabulariesEditable();
    }

    public Optional<URI> getVocabularyContext(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return Optional.ofNullable(editableVocabularies.get(vocabulary.getUri()));
    }
}
