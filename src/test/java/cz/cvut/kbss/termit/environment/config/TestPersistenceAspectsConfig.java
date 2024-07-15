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
package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.aspect.ChangeTrackingAspect;
import cz.cvut.kbss.termit.service.changetracking.ChangeTracker;
import org.aspectj.lang.Aspects;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestPersistenceAspectsConfig {

    @Bean
    public ChangeTrackingAspect changeTrackingAspect() {
        return Aspects.aspectOf(ChangeTrackingAspect.class);
    }

    @Bean
    @Primary
    public ChangeTracker changeTracker() {
        return mock(ChangeTracker.class);
    }
}
