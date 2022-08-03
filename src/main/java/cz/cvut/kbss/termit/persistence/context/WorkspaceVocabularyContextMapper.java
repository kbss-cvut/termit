package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

/**
 * {@link VocabularyContextMapper} implementation based on editable vocabularies.
 * <p>
 * When resolving vocabulary context, it is checked whether the specified vocabulary is edited. If so, the context is
 * resolved via {@link EditableVocabularies}, as the vocabulary will be an editable copy stored in a different context.
 */
@Component
@Primary
public class WorkspaceVocabularyContextMapper implements VocabularyContextMapper {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceVocabularyContextMapper.class);

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
                                   // Abusing Optional.map to peek on the wrapped instance and log
                                   .map(ctx -> {
                                       LOG.trace("Overriding canonical context of vocabulary {} with edited context {}.", uriToString(vocabularyUri), uriToString(ctx));
                                       return ctx;
                                   }).orElseGet(() -> delegatee.getVocabularyContext(vocabularyUri));
    }

    @Override
    public Optional<URI> getVocabularyInContext(URI contextUri) {
        return delegatee.getVocabularyInContext(contextUri);
    }
}
