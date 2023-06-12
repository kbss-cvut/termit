/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class SystemInitializer implements SmartInitializingSingleton, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(SystemInitializer.class);

    private final ApplicationContext appContext;

    @Autowired
    public SystemInitializer(ApplicationContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        LOG.info("Running startup tasks.");
        appContext.getBean(AdminAccountGenerator.class).initSystemAdmin();
        appContext.getBean(VocabularyAccessControlListGenerator.class).generateMissingAccessControlLists();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
