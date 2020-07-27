package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Tracks changes to assets.
 */
@Service
public class ChangeTracker {

    private final ChangeCalculator changeCalculator;

    private final ChangeRecordDao changeRecordDao;

    @Autowired
    public ChangeTracker(ChangeCalculator changeCalculator, ChangeRecordDao changeRecordDao) {
        this.changeCalculator = changeCalculator;
        this.changeRecordDao = changeRecordDao;
    }

    /**
     * Records an asset addition to the repository.
     *
     * @param added The added asset
     */
    @Transactional
    public void recordAddEvent(Asset added) {
        Objects.requireNonNull(added);
        final AbstractChangeRecord changeRecord = new PersistChangeRecord(added);
        changeRecord.setAuthor(SecurityUtils.currentUser().toUser());
        changeRecord.setTimestamp(Instant.now());
        changeRecordDao.persist(changeRecord, added);
    }

    /**
     * Records an asset update.
     * <p>
     * Each changed attribute is stored as a separate change record
     *
     * @param update   The updated version of the asset
     * @param original The original version of the asset
     */
    @Transactional
    public void recordUpdateEvent(Asset update, Asset original) {
        Objects.requireNonNull(update);
        Objects.requireNonNull(original);
        final Instant now = Instant.now();
        final User user = SecurityUtils.currentUser().toUser();
        changeCalculator.calculateChanges(update, original).forEach(ch -> {
            ch.setAuthor(user);
            ch.setTimestamp(now);
            changeRecordDao.persist(ch, update);
        });
    }
}
