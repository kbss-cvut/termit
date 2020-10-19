package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.PersistenceUtils;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.util.Configuration;

import java.util.List;

public abstract class WorkspaceBasedAssetDao<T extends Asset> extends AssetDao<T> {

    protected final PersistenceUtils persistenceUtils;

    protected WorkspaceBasedAssetDao(Class<T> type, EntityManager em, Configuration config,
                                     DescriptorFactory descriptorFactory,
                                     PersistenceUtils persistenceUtils) {
        super(type, em, config, descriptorFactory);
        this.persistenceUtils = persistenceUtils;
    }

    @Override
    public List<RecentlyModifiedAsset> findLastEdited(int limit) {
        // TODO
        return super.findLastEdited(limit);
    }

    @Override
    public List<RecentlyModifiedAsset> findLastEditedBy(User author, int limit) {
        // TODO
        return super.findLastEditedBy(author, limit);
    }
}
