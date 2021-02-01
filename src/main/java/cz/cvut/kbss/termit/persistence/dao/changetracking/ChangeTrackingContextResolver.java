package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import static cz.cvut.kbss.termit.util.Constants.DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION;

/**
 * Determines repository context into which change tracking records are stored.
 */
@Component
public class ChangeTrackingContextResolver {

    private final WorkspaceMetadataProvider workspaceMetadataProvider;

    private final VocabularyDao vocabularyDao;

    @Autowired
    public ChangeTrackingContextResolver(WorkspaceMetadataProvider workspaceMetadataProvider,
                                         VocabularyDao vocabularyDao) {
        this.workspaceMetadataProvider = workspaceMetadataProvider;
        this.vocabularyDao = vocabularyDao;
    }

    /**
     * Resolves change tracking context of the specified changed asset.
     * <p>
     * In general, each vocabulary has its own change tracking context, so changes to it and all its terms are stored in
     * this context.
     *
     * @param changedAsset Asset for which change records will be generated
     * @return Identifier of the change tracking context of the specified asset
     */
    public URI resolveChangeTrackingContext(Asset<?> changedAsset) {
        Objects.requireNonNull(changedAsset);
        if (changedAsset instanceof Vocabulary) {
            return workspaceMetadataProvider.getCurrentWorkspaceMetadata().getVocabularyInfo(changedAsset.getUri())
                                            .getChangeTrackingContext();
        } else if (changedAsset instanceof Term) {
            final Term t = (Term) changedAsset;
            final Optional<Vocabulary> vocabulary = vocabularyDao.findVocabularyOfGlossary(t.getGlossary());
            final URI vocabularyUri = vocabulary
                    .orElseThrow(() -> new NotFoundException("Vocabulary for term " + t + " not found!")).getUri();
            return workspaceMetadataProvider.getCurrentWorkspaceMetadata().getVocabularyInfo(vocabularyUri)
                                            .getChangeTrackingContext();
        }
        return URI.create(changedAsset.getUri().toString().concat(DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION));
    }
}
