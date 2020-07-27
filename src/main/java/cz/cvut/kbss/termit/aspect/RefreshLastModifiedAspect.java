package cz.cvut.kbss.termit.aspect;

import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures corresponding last modified timestamp is refreshed when a data modifying operation is invoked for an asset.
 */
@Aspect
public class RefreshLastModifiedAspect {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshLastModifiedAspect.class);

    @Pointcut(value = "@annotation(cz.cvut.kbss.termit.asset.provenance.ModifiesData) " +
            "&& target(cz.cvut.kbss.termit.asset.provenance.SupportsLastModification)")
    public void dataModificationOperation() {
    }

    @After(value = "dataModificationOperation() && target(bean)", argNames = "bean")
    public void refreshLastModified(SupportsLastModification bean) {
        LOG.trace("Refreshing last modified timestamp on bean {}.", bean);
        bean.refreshLastModified();
    }
}
