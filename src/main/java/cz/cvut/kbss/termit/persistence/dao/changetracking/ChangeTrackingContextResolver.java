package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.workspace.WorkspaceMetadataCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

import static cz.cvut.kbss.termit.util.Constants.DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION;

/**
 * Determines repository context into which change tracking records are stored.
 */
@Component
public class ChangeTrackingContextResolver {

    private final WorkspaceMetadataCache workspaceMetadataCache;

    @Autowired
    public ChangeTrackingContextResolver(WorkspaceMetadataCache workspaceMetadataCache) {
        this.workspaceMetadataCache = workspaceMetadataCache;
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
    public URI resolveChangeTrackingContext(Asset changedAsset) {
        Objects.requireNonNull(changedAsset);
        if (changedAsset instanceof Vocabulary) {
            return workspaceMetadataCache.getCurrentWorkspaceMetadata().getVocabularyInfo(changedAsset.getUri())
                                         .getChangeTrackingContext();
        } else if (changedAsset instanceof Term) {
            final Term t = (Term) changedAsset;
            return workspaceMetadataCache.getCurrentWorkspaceMetadata().getVocabularyInfo(t.getVocabulary())
                                         .getChangeTrackingContext();
        }
        return URI.create(changedAsset.getUri().toString().concat(DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION));
    }
}
