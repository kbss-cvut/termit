package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.UpdateChangeRecordRollbackException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Term_;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Vocabulary_;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord_;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.business.RudService;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


class ChangeRollbackIntegrationTest extends BaseServiceTestRunner {
    private static final URI PROPERTY = URI.create("http://example.com/property");
    private static final URI URI_PROPERTY = URI.create("http://example.com/property/uri");
    private static final URI PROPERTY_B = URI.create("http://example.com/property/b");

    @Autowired
    private EntityManager em;

    @Autowired
    private TermService termService;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private ChangeRollbackService sut;

    @MockitoSpyBean
    private ChangeTracker changeTracker;

    @Autowired
    private ChangeRecordService changeRecordService;

    @Autowired
    private DataRepositoryService dataRepositoryService;

    @MockitoBean
    private TextAnalysisService textAnalysisService; // no-op analysis service

    private Vocabulary vocabulary;

    private Term term;
    private Term termB;
    private Term termC;
    private Term termD;

    final Set<Object> originalPropertyValue = Set.of("first value", "second value");

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);

        final User author = Generator.generateUserWithId();
        author.addType(UserRole.ADMIN.getType());
        Environment.setCurrentUser(author);

        transactional(() -> em.persist(author));

        vocabulary = Generator.generateVocabularyWithId();
        term = Generator.generateTermWithId(vocabulary.getUri());
        termB = Generator.generateTermWithId(vocabulary.getUri());
        termC = Generator.generateTermWithId(vocabulary.getUri());
        termD = Generator.generateTermWithId(vocabulary.getUri());

        term.setProperties(new HashMap<>());

        term.getProperties().put(PROPERTY.toString(), new HashSet<>(originalPropertyValue));
        term.getProperties().put(URI_PROPERTY.toString(), new HashSet<>(Set.of(PROPERTY)));

        term.setRelated(new HashSet<>());
        term.getRelated().add(termB.toTermInfo());
        term.getRelated().add(termC.toTermInfo());

        vocabularyService.persist(vocabulary);

        termService.persistRoot(termB, vocabulary);
        termService.persistRoot(termC, vocabulary);
        termService.persistRoot(termD, vocabulary);

        termService.persistRoot(term, vocabulary);
    }

    private static ChangeRecordFilterDto changeFilter() {
        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto();
        filter.setChangeType(UpdateChangeRecord_.entityClassIRI.toURI());
        return filter;
    }

    private MultilingualString makeCopy(MultilingualString original) {
        return new MultilingualString(original.getValue());
    }

    private UpdateChangeRecord getRecord(Asset<?> changedAsset) {
        return (UpdateChangeRecord) changeRecordService
                .getChanges(changedAsset, changeFilter()).getFirst();
    }

    private <T extends Asset<?>> T update(T entity, RudService<T> service) {
        service.update(entity);
        return service.findRequired(entity.getUri());
    }

    @Test
    void rollbackVocabularyLabelPersistsOriginalValue() {
        final MultilingualString originalValue = makeCopy(vocabulary.getLabel());

        vocabulary.setLabel(Environment.LANGUAGE, "new vocabulary label");
        vocabulary = update(vocabulary, vocabularyService);

        // verify change event was handled by change tracker
        verify(changeTracker).onAssetUpdateEvent(any());

        // retrieve change record created by change tracker
        final UpdateChangeRecord record = getRecord(vocabulary);

        assertEquals(Vocabulary_.labelPropertyIRI.toURI(), record.getChangedAttribute());
        assertEquals(Set.of(originalValue), record.getOriginalValue());
        assertEquals(Set.of(vocabulary.getLabel()), record.getNewValue());

        sut.rollback(record);


        final Vocabulary rollbacked = vocabularyService.findRequired(vocabulary.getUri());
        assertEquals(originalValue, rollbacked.getLabel());
    }

    @Test
    void rollbackRelatedTermRollsbackToOriginalValue() {
        final Set<TermInfo> originalValue = Set.copyOf(term.getRelated());

        term.getRelated().remove(termB.toTermInfo());
        // termC remains
        term.getRelated().add(termD.toTermInfo());
        term = update(term, termService);

        assertEquals(Set.of(termC.toTermInfo(), termD.toTermInfo()), term.getRelated());

        final UpdateChangeRecord record = getRecord(term);
        sut.rollback(record);

        term = termService.findRequired(term.getUri());

        assertEquals(originalValue, term.getRelated());
    }

    @Test
    void rollbackRelatedTermThrowsWhenOriginalRelatedTermDoesNotExist() {
        final Set<TermInfo> originalValue = Set.copyOf(term.getRelated());

        term.getRelated().remove(termB.toTermInfo());
        // termC remains
        term = update(term, termService);

        final Set<TermInfo> newValue = Set.of(termC.toTermInfo());
        assertEquals(newValue, term.getRelated());
        termService.remove(termB);

        final UpdateChangeRecord record = getRecord(term);

        assertFalse(termService.exists(termB.getUri()));
        assertTrue(record.getOriginalValue().contains(termB.getUri()));

        assertThrows(UpdateChangeRecordRollbackException.class, () -> sut.rollback(record));

        term = termService.findRequired(term.getUri());
        assertEquals(newValue, term.getRelated(), "The related terms must not change when rollback fails");
        assertNotEquals(originalValue, term.getRelated());
    }

    @Test
    void rollbackNativePrimitivePropertyPersistsOriginalValue() {
        final String newValue = "new value";
        term.getProperties().get(PROPERTY.toString()).add(newValue);
        term = update(term, termService);

        assertNotEquals(originalPropertyValue, term.getProperties().get(PROPERTY.toString()));

        final UpdateChangeRecord record = getRecord(term);

        assertEquals(3, record.getNewValue().size());
        assertTrue(record.getNewValue().containsAll(originalPropertyValue));
        assertTrue(record.getNewValue().contains(newValue));

        sut.rollback(record);

        term = termService.findRequired(term.getUri());
        assertEquals(originalPropertyValue, term.getProperties().get(PROPERTY.toString()));
        assertFalse(term.getProperties().containsKey(PROPERTY_B.toString()));
        assertEquals(1, term.getProperties().size());
    }

    /**
     * TermIt cannot guarantee that the reference should not be valid Term/Vocabulary
     */
    @Test
    void rollbackThrowsForRollbackOfNativeURIProperty() {
        term.getProperties().remove(URI_PROPERTY.toString());
        term = update(term, termService);

        assertFalse(term.getProperties().containsKey(URI_PROPERTY.toString()));

        final UpdateChangeRecord record = getRecord(term);
        assertThrows(UpdateChangeRecordRollbackException.class, () -> sut.rollback(record));
    }

    private CustomAttribute persistUriCustomAttribute() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setUri(URI_PROPERTY);
        customAttribute.setLabel(MultilingualString.create("General URI resource", null));
        customAttribute.setDomain(Term_.entityClassIRI.toURI());
        customAttribute.setRange(URI.create(RDFS.RESOURCE));
        dataRepositoryService.persistCustomAttribute(customAttribute);
        return customAttribute;
    }

    @Test
    void rollbackOfNativeURICustomAttributePersistsOriginalValue() {
        persistUriCustomAttribute();

        term.getProperties().remove(URI_PROPERTY.toString());
        term = update(term, termService);

        assertFalse(term.getProperties().containsKey(URI_PROPERTY.toString()));

        final UpdateChangeRecord record = getRecord(term);
        sut.rollback(record);

        term = termService.findRequired(term.getUri());
        assertTrue(term.getProperties().containsKey(URI_PROPERTY.toString()));
        assertTrue(term.getProperties().get(URI_PROPERTY.toString()).contains(PROPERTY));
    }

    @Test
    void rollbackOfCustomAttributeWithUnknownRangeThrows() {
        CustomAttribute attribute = persistUriCustomAttribute();
        attribute.setRange(PROPERTY_B);
        dataRepositoryService.updateCustomAttribute(attribute);

        term.getProperties().remove(URI_PROPERTY.toString());
        term = update(term, termService);

        assertFalse(term.getProperties().containsKey(URI_PROPERTY.toString()));

        final UpdateChangeRecord record = getRecord(term);
        assertThrows(UpdateChangeRecordRollbackException.class, () -> sut.rollback(record));
    }
}
