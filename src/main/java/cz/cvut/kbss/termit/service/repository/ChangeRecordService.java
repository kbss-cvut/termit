package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ChangeRecordService implements ChangeRecordProvider<Asset<?>> {

    private final ChangeRecordDao changeRecordDao;

    @Autowired
    public ChangeRecordService(ChangeRecordDao changeRecordDao) {
        this.changeRecordDao = changeRecordDao;
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Asset<?> asset) {
        return changeRecordDao.findAll(asset);
    }

    /**
     * Gets authors of the specified asset.
     * <p>
     * This method returns a collection because some assets may have multiple authors (e.g., when a vocabulary is
     * re-imported from SKOS by a different user). Also, some assets may not have recorded authors (e.g., terms from an
     * imported vocabulary), in which case the result of this method is empty.
     *
     * @param asset Asset whose authors to get
     * @return A set of zero or more authors of the specified asset
     */
    public Set<User> getAuthors(Asset<?> asset) {
        return changeRecordDao.getAuthors(asset);
    }
}
