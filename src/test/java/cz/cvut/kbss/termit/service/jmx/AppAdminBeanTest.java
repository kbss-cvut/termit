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
package cz.cvut.kbss.termit.service.jmx;

import cz.cvut.kbss.termit.event.EvictCacheEvent;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppAdminBeanTest {

    @Mock
    private ApplicationEventPublisher eventPublisherMock;

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private Configuration configuration;

    @InjectMocks
    private AppAdminBean sut;

    @Test
    void invalidateCachesPublishesEvictCacheEvent() {
        sut.invalidateCaches();
        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisherMock, atLeastOnce()).publishEvent(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(EvictCacheEvent.class::isInstance));
    }

    @Test
    void invalidateCachesPublishesRefreshLastModifiedEvent() {
        sut.invalidateCaches();
        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisherMock, atLeastOnce()).publishEvent(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(RefreshLastModifiedEvent.class::isInstance));
    }
}
