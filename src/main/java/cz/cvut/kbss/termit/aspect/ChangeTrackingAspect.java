package cz.cvut.kbss.termit.aspect;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeTrackingHelperDao;
import cz.cvut.kbss.termit.service.changetracking.ChangeTracker;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Records changes to assets based on modification operations.
 */
@Aspect
public class ChangeTrackingAspect {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeTrackingAspect.class);

    @Autowired
    private ChangeTracker changeTracker;

    @Autowired
    private ChangeTrackingHelperDao helperDao;

    @Pointcut(value = "execution(public void persist(..)) && target(cz.cvut.kbss.termit.persistence.dao.GenericDao) " +
            "&& @args(cz.cvut.kbss.termit.model.changetracking.Audited)")
    public void persistOperation() {
    }

    @Pointcut(value = "execution(public void persist(..)) && target(cz.cvut.kbss.termit.persistence.dao.TermDao) " +
            "&& @args(cz.cvut.kbss.termit.model.changetracking.Audited, *)")
    public void persistTermOperation() {
    }

    @Pointcut(value = "execution(public * update(..)) && target(cz.cvut.kbss.termit.persistence.dao.GenericDao) " +
            "&& @args(cz.cvut.kbss.termit.model.changetracking.Audited)")
    public void updateOperation() {
    }

    @After(value = "persistOperation() && args(asset)")
    public void recordAssetPersist(Asset<?> asset) {
        LOG.trace("Recording creation of asset {}.", asset);
        changeTracker.recordAddEvent(asset);
    }

    @After(value = "persistTermOperation() && args(asset, voc)", argNames = "asset,voc")
    public void recordTermPersist(Term asset, Vocabulary voc) {
        LOG.trace("Recording creation of term {}.", asset);
        changeTracker.recordAddEvent(asset);
    }

    @Before(value = "updateOperation() && args(asset)")
    public void recordAssetUpdate(Asset<?> asset) {
        LOG.trace("Recording update of asset {}.", asset);
        changeTracker.recordUpdateEvent(asset, helperDao.findStored(asset));
    }
}
