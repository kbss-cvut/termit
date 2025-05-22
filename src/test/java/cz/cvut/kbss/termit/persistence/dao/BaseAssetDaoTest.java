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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.AssetPersistEvent;
import cz.cvut.kbss.termit.event.AssetUpdateEvent;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.Environment.setPrimaryLabel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

class BaseAssetDaoTest extends BaseDaoTestRunner{

    @Autowired
    private EntityManager em;

    @Autowired
    private Configuration config;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private BaseDao<Term> sut;

    @BeforeEach
    void setUp() {
        this.sut = new BaseAssetDaoImpl(em, config.getPersistence(), descriptorFactory);
        sut.setApplicationEventPublisher(eventPublisher);
    }

    @Test
    void persistPublishesAssetPersistEvent() {
        final Term t = Generator.generateTermWithId();

        transactional(() -> sut.persist(t));
        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        final Optional<AssetPersistEvent> evt = captor.getAllValues().stream()
                                                      .filter(AssetPersistEvent.class::isInstance)
                                                      .map(AssetPersistEvent.class::cast).findFirst();
        assertTrue(evt.isPresent());
        assertEquals(t, evt.get().getAsset());
    }

    @Test
    void updatePublishesAssetUpdateEvent() {
        final Term t = Generator.generateTermWithId();
        transactional(() -> em.persist(t));
        setPrimaryLabel(t, "Updated primary label");

        transactional(() -> sut.update(t));

        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        final Optional<AssetUpdateEvent> evt = captor.getAllValues().stream()
                                                     .filter(AssetUpdateEvent.class::isInstance)
                                                     .map(AssetUpdateEvent.class::cast).findFirst();
        assertTrue(evt.isPresent());
        assertEquals(t, evt.get().getAsset());
    }

    private static class BaseAssetDaoImpl extends BaseAssetDao<Term> {

        BaseAssetDaoImpl(EntityManager em, Configuration.Persistence config, DescriptorFactory descriptorFactory) {
            super(Term.class, em, config, descriptorFactory);
        }

        @Override
        protected URI labelProperty() {
            return URI.create(SKOS.PREF_LABEL);
        }
    }
}
