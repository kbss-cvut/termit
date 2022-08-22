package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * {@link VocabularyContextMapper} implementation based on editable vocabularies.
 * <p>
 * When resolving vocabulary context, it is checked whether the specified vocabulary is edited. If so, the context is
 * resolved via {@link EditableVocabularies}, as the vocabulary will be an editable copy stored in a different context.
 */
@Component
@Primary
public class WorkspaceVocabularyContextMapper implements VocabularyContextMapper {

    private final VocabularyContextMapper delegatee;

    private final EditableVocabularies editableVocabularies;

    public WorkspaceVocabularyContextMapper(VocabularyContextMapper delegatee,
                                            EditableVocabularies editableVocabularies) {
        this.delegatee = delegatee;
        this.editableVocabularies = editableVocabularies;
    }

    @Override
    public URI getVocabularyContext(URI vocabularyUri) {
        return editableVocabularies.getVocabularyContext(vocabularyUri)
                                   .orElseGet(() -> delegatee.getVocabularyContext(vocabularyUri));
    }

    @Override
    public Optional<URI> getVocabularyInContext(URI contextUri) {
        return delegatee.getVocabularyInContext(contextUri);
    }
}
