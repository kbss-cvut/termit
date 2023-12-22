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
