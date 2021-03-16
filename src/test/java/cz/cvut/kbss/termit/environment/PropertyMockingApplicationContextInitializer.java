/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.environment;

import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Uses {@link MockEnvironment}, which supports setting environment property values at runtime.
 */
public class PropertyMockingApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        final Environment realEnvironment = configurableApplicationContext.getEnvironment();
        MockEnvironment mockEnvironment = new MockEnvironment();
        RandomValuePropertySource.addToEnvironment(mockEnvironment);
        DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
        ConfigDataEnvironmentPostProcessor.applyTo(mockEnvironment, configurableApplicationContext, bootstrapContext);
        bootstrapContext.close(configurableApplicationContext);
        DefaultPropertiesPropertySource.moveToEnd(mockEnvironment);
        mockEnvironment.setActiveProfiles(realEnvironment.getActiveProfiles());
        configurableApplicationContext.setEnvironment(mockEnvironment);
    }
}
