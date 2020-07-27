package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ChangeRecordService implements ChangeRecordProvider<Asset> {

    private final ChangeRecordDao changeRecordDao;

    @Autowired
    public ChangeRecordService(ChangeRecordDao changeRecordDao) {
        this.changeRecordDao = changeRecordDao;
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Asset asset) {
        Objects.requireNonNull(asset);
        return changeRecordDao.findAll(asset);
    }
}
