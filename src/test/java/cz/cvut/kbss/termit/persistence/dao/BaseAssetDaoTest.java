package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.AssetPersistEvent;
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

import static org.junit.jupiter.api.Assertions.*;
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
