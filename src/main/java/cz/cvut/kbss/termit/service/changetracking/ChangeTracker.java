package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Tracks changes to assets.
 */
@Service
public class ChangeTracker {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeTracker.class);

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
    public void recordAddEvent(Asset<?> added) {
        Objects.requireNonNull(added);
        final AbstractChangeRecord changeRecord = new PersistChangeRecord(added);
        changeRecord.setAuthor(SecurityUtils.currentUser().toUser());
        changeRecord.setTimestamp(Utils.timestamp());
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
    public void recordUpdateEvent(Asset<?> update, Asset<?> original) {
        Objects.requireNonNull(update);
        Objects.requireNonNull(original);
        final Instant now = Utils.timestamp();
        final User user = SecurityUtils.currentUser().toUser();
        final Collection<UpdateChangeRecord> changes = changeCalculator.calculateChanges(update, original);
        if (!changes.isEmpty()) {
            LOG.trace("Found changes to attributes: " + changes.stream().map(ch -> ch.getChangedAttribute().toString())
                                                               .collect(Collectors.joining(", ")));
        }
        changes.forEach(ch -> {
            ch.setAuthor(user);
            ch.setTimestamp(now);
            changeRecordDao.persist(ch, update);
        });
    }
}
