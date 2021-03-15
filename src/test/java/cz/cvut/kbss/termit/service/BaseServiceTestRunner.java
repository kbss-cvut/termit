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
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.TransactionalTestRunner;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.net.URI;

@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableTransactionManagement
@Import({TestConfig.class})
@SpringBootTest
@ContextConfiguration(initializers = {ConfigDataApplicationContextInitializer.class})
@ActiveProfiles("test")
public class BaseServiceTestRunner extends TransactionalTestRunner {

    private static final String EXISTENCE_CHECK_QUERY = "ASK { ?x a ?type . }";

    protected void verifyInstancesDoNotExist(String type, EntityManager em) {
        Assertions.assertFalse(
                em.createNativeQuery(EXISTENCE_CHECK_QUERY, Boolean.class).setParameter("type", URI.create(type))
                  .getSingleResult());
    }
}
