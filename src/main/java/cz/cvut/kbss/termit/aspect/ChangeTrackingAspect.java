/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

import java.net.URI;

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

    @Pointcut(value = "execution(public void setState(..)) && target(cz.cvut.kbss.termit.persistence.dao.TermDao)" +
            "&& @args(cz.cvut.kbss.termit.model.changetracking.Audited, *)")
    public void termStateUpdateOperation() {
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

    @Before(value = "termStateUpdateOperation() && args(asset, state)", argNames = "asset, state")
    public void recordTermStateUpdate(Term asset, URI state) {
        LOG.trace("Recording update of asset {}.", asset);
        final Term original = new Term();
        original.setUri(asset.getUri());
        original.setState(helperDao.findStored(asset).getState());
        final Term update = new Term();
        update.setUri(asset.getUri());
        update.setState(state);
        changeTracker.recordUpdateEvent(update, original);
    }
}
