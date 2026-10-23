package cz.cvut.kbss.termit.service.repository.customization;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.AssetChangesRecordedEvent;
import cz.cvut.kbss.termit.event.CustomAttributeMadeSymmetricEvent;
import cz.cvut.kbss.termit.event.TermReferencesUpdatedEvent;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

class SymmetricCustomAttributeSynchronizerTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private SymmetricCustomAttributeSynchronizer sut;

    @MockitoSpyBean
    private TermDao termDao;

    @Test
    void onAssetChangeRecordedAddsInverseDirectionToNewSymmetricCustomAttributeAssertions() {
        final CustomAttribute att = initSymmetricCustomAttribute();
        final Term tOne = Generator.generateTermWithId();
        final Term tTwo = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(att, new EntityDescriptor(URI.create(CustomAttribute.CONTEXT)));
            em.persist(tOne, new EntityDescriptor(tOne.getUri()));
            em.persist(tTwo, new EntityDescriptor(tTwo.getUri()));
        });

        transactional(() -> {
            tOne.setProperties(Map.of(att.getUri().toString(), Set.of(tTwo.getUri())));
            final UpdateChangeRecord change = new UpdateChangeRecord(tOne);
            change.setChangedAttribute(att.getUri());
            change.setOriginalValue(null);
            change.setNewValue(Set.of(tTwo.getUri()));
            sut.onAssetChangeRecorded(new AssetChangesRecordedEvent<>(this, List.of(change)));
        });

        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?tTwo ?att ?tOne . }}", Boolean.class)
                .setParameter("tTwo", tTwo)
                .setParameter("tOne", tOne)
                .setParameter("att", att)
                .getSingleResult());
    }

    @Test
    void onAssetChangeRecordedRemovesInverseDirectionOfRemovedSymmetricCustomAttributeAssertions() {
        final CustomAttribute att = initSymmetricCustomAttribute();
        final Term tOne = Generator.generateTermWithId();
        final Term tTwo = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(att, new EntityDescriptor(URI.create(CustomAttribute.CONTEXT)));
            em.persist(tOne, new EntityDescriptor(tOne.getUri()));
            em.persist(tTwo, new EntityDescriptor(tTwo.getUri()));
        });

        transactional(() -> {
            tOne.setProperties(Map.of(att.getUri().toString(), Set.of(tTwo.getUri())));
            final UpdateChangeRecord change = new UpdateChangeRecord(tOne);
            change.setChangedAttribute(att.getUri());
            change.setOriginalValue(null);
            change.setNewValue(Set.of(tTwo.getUri()));
            sut.onAssetChangeRecorded(new AssetChangesRecordedEvent<>(this, List.of(change)));
        });
        transactional(() -> {
            tOne.setProperties(Map.of());
            final UpdateChangeRecord change = new UpdateChangeRecord(tOne);
            change.setChangedAttribute(att.getUri());
            change.setOriginalValue(Set.of(tTwo.getUri()));
            change.setNewValue(null);
            sut.onAssetChangeRecorded(new AssetChangesRecordedEvent<>(this, List.of(change)));
        });

        assertFalse(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?tTwo ?att ?tOne . }}", Boolean.class)
                .setParameter("tTwo", tTwo)
                .setParameter("tOne", tOne)
                .setParameter("att", att)
                .getSingleResult());
    }

    @Test
    void onAssetChangeRecordedPublishesTermReferencesUpdatedEventForNewlyReferencedTerm() {
        final CustomAttribute att = initSymmetricCustomAttribute();
        final Term tOne = Generator.generateTermWithId();
        final Term tTwo = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(att, new EntityDescriptor(URI.create(CustomAttribute.CONTEXT)));
            em.persist(tOne, new EntityDescriptor(tOne.getUri()));
            em.persist(tTwo, new EntityDescriptor(tTwo.getUri()));
        });

        transactional(() -> {
            tOne.setProperties(Map.of(att.getUri().toString(), Set.of(tTwo.getUri())));
            final UpdateChangeRecord change = new UpdateChangeRecord(tOne);
            change.setChangedAttribute(att.getUri());
            change.setOriginalValue(null);
            change.setNewValue(Set.of(tTwo.getUri()));
            sut.onAssetChangeRecorded(new AssetChangesRecordedEvent<>(this, List.of(change)));
        });

        final ArgumentCaptor<TermReferencesUpdatedEvent> captor =
                ArgumentCaptor.forClass(TermReferencesUpdatedEvent.class);
        verify(termDao).onTermReferencesUpdated(captor.capture());
        final TermReferencesUpdatedEvent evt = captor.getValue();
        assertEquals(tTwo.getUri(), evt.getTermUri());
        assertEquals(att.getUri().toString(), evt.getProperty());
        assertTrue(evt.getContext().isPresent());
        assertEquals(tTwo.getUri(), evt.getContext().get());
    }

    @Test
    void onAssetChangeRecordedPublishesTermReferencesUpdatedEventForTermThatCeasedToBeReferenced() {
        final CustomAttribute att = initSymmetricCustomAttribute();
        final Term tOne = Generator.generateTermWithId();
        final Term tTwo = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(att, new EntityDescriptor(URI.create(CustomAttribute.CONTEXT)));
            em.persist(tOne, new EntityDescriptor(tOne.getUri()));
            em.persist(tTwo, new EntityDescriptor(tTwo.getUri()));
        });

        transactional(() -> {
            tOne.setProperties(Map.of(att.getUri().toString(), Set.of(tTwo.getUri())));
            final UpdateChangeRecord change = new UpdateChangeRecord(tOne);
            change.setChangedAttribute(att.getUri());
            change.setOriginalValue(null);
            change.setNewValue(Set.of(tTwo.getUri()));
            sut.onAssetChangeRecorded(new AssetChangesRecordedEvent<>(this, List.of(change)));
        });
        clearInvocations(termDao);
        transactional(() -> {
            tOne.setProperties(Map.of());
            final UpdateChangeRecord change = new UpdateChangeRecord(tOne);
            change.setChangedAttribute(att.getUri());
            change.setOriginalValue(Set.of(tTwo.getUri()));
            change.setNewValue(null);
            sut.onAssetChangeRecorded(new AssetChangesRecordedEvent<>(this, List.of(change)));
        });

        final ArgumentCaptor<TermReferencesUpdatedEvent> captor =
                ArgumentCaptor.forClass(TermReferencesUpdatedEvent.class);
        verify(termDao).onTermReferencesUpdated(captor.capture());
        final TermReferencesUpdatedEvent evt = captor.getValue();
        assertEquals(tTwo.getUri(), evt.getTermUri());
        assertEquals(att.getUri().toString(), evt.getProperty());
        assertTrue(evt.getContext().isPresent());
        assertEquals(tTwo.getUri(), evt.getContext().get());
    }

    private CustomAttribute initSymmetricCustomAttribute() {
        final CustomAttribute att =
                new CustomAttribute(Generator.generateUri(), MultilingualString.create("Test attribute", "en"), null);
        att.setDomain(URI.create(SKOS.CONCEPT));
        att.setRange(att.getDomain());
        // TODO Use JOPA OWL constant when available in the next release
        att.addType(OWL.SYMMETRICPROPERTY.stringValue());
        return att;
    }

    @Test
    void onCustomAttributeMadeSymmetricInsertsInverseStatementsForExistingAttributeAssertionsIntoCorrectContexts() {
        final CustomAttribute att = initSymmetricCustomAttribute();
        final Term tOne = Generator.generateTermWithId();
        final Term tTwo = Generator.generateTermWithId();
        tOne.setProperties(Map.of(att.getUri().toString(), Set.of(tTwo.getUri())));
        transactional(() -> {
            em.persist(att, new EntityDescriptor(URI.create(CustomAttribute.CONTEXT)));
            em.persist(tOne, new EntityDescriptor(tOne.getUri()));
            em.persist(tTwo, new EntityDescriptor(tTwo.getUri()));
        });

        sut.onCustomAttributeMadeSymmetric(new CustomAttributeMadeSymmetricEvent(this, att));
        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?tTwo ?att ?tOne } }", Boolean.class)
                .setParameter("tTwo", tTwo)
                .setParameter("att", att)
                .setParameter("tOne", tOne)
                .setParameter("g", tTwo)
                .getSingleResult());
    }
}
