/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.util.Configuration;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.mockito.Answers.RETURNS_SMART_NULLS;

@TestConfiguration
@EnableAspectJAutoProxy
@ImportResource("classpath*:spring-aop.xml")
@ComponentScan(value = "cz.cvut.kbss.termit.util.throttle")
public class ThrottleAspectTestContextConfig {

    @Bean
    public ThreadPoolTaskScheduler longRunningTaskScheduler() {
        return Mockito.mock(ThreadPoolTaskScheduler.class, RETURNS_SMART_NULLS);
    }

    @Bean
    public ThrottledService throttledService() {
        return new ThrottledService();
    }

    @Bean
    public Configuration configuration() {
        return new Configuration();
    }
}
