/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.jmx;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.event.VocabularyContentModified;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppAdminBeanTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private EntityManager em;

    @Mock
    private ApplicationEventPublisher eventPublisherMock;

    private AppAdminBean sut;

    @BeforeEach
    void setUp() {
        this.sut = new AppAdminBean(eventPublisherMock, emf);
    }

    @Test
    void invalidateCachesClearsPersistenceSecondLevelCache() {
        final User entity = Generator.generateUserWithId();
        transactional(() -> em.persist(entity));
        assertTrue(emf.getCache().contains(User.class, entity.getUri(), new EntityDescriptor()));
        sut.invalidateCaches();
        assertFalse(emf.getCache().contains(User.class, entity.getUri(), new EntityDescriptor()));
    }

    @Test
    void invalidateCachesPublishesRefreshLastModifiedEvent() {
        sut.invalidateCaches();
        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisherMock, atLeastOnce()).publishEvent(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(RefreshLastModifiedEvent.class::isInstance));
    }

    @Test
    void invalidateCachesPublishesVocabularyContentModifiedEventToForceEvictionOfVocabularyContentBasedCaches() {
        sut.invalidateCaches();
        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisherMock, atLeastOnce()).publishEvent(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(VocabularyContentModified.class::isInstance));
    }
}
