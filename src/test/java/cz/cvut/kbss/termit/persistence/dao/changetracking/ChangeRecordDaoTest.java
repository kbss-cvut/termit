package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ChangeRecordDaoTest extends BaseDaoTestRunner {

    @Autowired
    private ChangeTrackingContextResolver contextResolver;

    @Autowired
    private EntityManager em;

    @Autowired
    private ChangeRecordDao sut;

    private User author;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
    }

    @Test
    void persistSavesChangeRecordIntoContext() {
        final AbstractChangeRecord record = generatePersistRecord(Instant.now(), Generator.generateUri());
        transactional(() -> sut.persist(record, vocabulary));

        final AbstractChangeRecord result = em.find(AbstractChangeRecord.class, record.getUri());
        assertNotNull(result);
        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?x a ?changeRecord . } }", Boolean.class)
                     .setParameter("g", contextResolver.resolveChangeTrackingContext(vocabulary))
                     .setParameter("x", record.getUri())
                     .getSingleResult());
    }

    private PersistChangeRecord generatePersistRecord(Instant timestamp, URI changedObject) {
        final PersistChangeRecord record = new PersistChangeRecord();
        record.setAuthor(author);
        record.setTimestamp(timestamp);
        record.setChangedEntity(changedObject);
        return record;
    }

    private UpdateChangeRecord generateUpdateRecord(Instant timestamp, URI changedObject) {
        final UpdateChangeRecord record = new UpdateChangeRecord();
        record.setAuthor(author);
        record.setTimestamp(timestamp);
        record.setChangedEntity(changedObject);
        record.setChangedAttribute(URI.create(SKOS.PREF_LABEL));
        return record;
    }

    @Test
    void findAllRetrievesChangeRecordsRelatedToSpecifiedAsset() {
        enableRdfsInference(em);
        final Term asset = Generator.generateTermWithId();
        final List<AbstractChangeRecord> records = IntStream.range(0, 5).mapToObj(
                i -> generateUpdateRecord(Instant.ofEpochMilli(System.currentTimeMillis() - i * 10000L),
                        asset.getUri())).collect(Collectors.toList());
        transactional(() -> records.forEach(r -> em.persist(r, persistDescriptor(vocabulary.getUri()))));

        final List<AbstractChangeRecord> result = sut.findAll(asset);
        assertEquals(records.size(), result.size());
        assertTrue(records.containsAll(result));
    }

    private Descriptor persistDescriptor(URI context) {
        final EntityDescriptor descriptor = new EntityDescriptor(context);
        descriptor.addAttributeDescriptor(em.getMetamodel().entity(AbstractChangeRecord.class).getAttribute("author"),
                new EntityDescriptor());
        return descriptor;
    }

    @Test
    void findAllReturnsChangeRecordsOrderedByTimestampDescending() {
        enableRdfsInference(em);
        final Term asset = Generator.generateTermWithId();
        final List<AbstractChangeRecord> records = IntStream.range(0, 5).mapToObj(
                i -> generateUpdateRecord(Instant.ofEpochMilli(System.currentTimeMillis() + i * 10000L),
                        asset.getUri())).collect(Collectors.toList());
        transactional(() -> records.forEach(r -> em.persist(r, persistDescriptor(vocabulary.getUri()))));

        final List<AbstractChangeRecord> result = sut.findAll(asset);
        records.sort(Comparator.comparing(AbstractChangeRecord::getTimestamp).reversed());
        assertEquals(records, result);
    }

    @Test
    void findAllReturnsChangeRecordsOrderedByTimestampDescendingAndChangedAttributeId() {
        enableRdfsInference(em);
        final Term asset = Generator.generateTermWithId();
        final Instant now = Instant.now();
        final UpdateChangeRecord rOne = generateUpdateRecord(now, asset.getUri());
        rOne.setChangedAttribute(URI.create(SKOS.PREF_LABEL));
        final UpdateChangeRecord rTwo = generateUpdateRecord(now, asset.getUri());
        rTwo.setChangedAttribute(URI.create(SKOS.DEFINITION));
        transactional(() -> {
            em.persist(rOne, persistDescriptor(vocabulary.getUri()));
            em.persist(rTwo, persistDescriptor(vocabulary.getUri()));
        });

        final List<AbstractChangeRecord> result = sut.findAll(asset);
        assertEquals(2, result.size());
        result.forEach(r -> assertThat(r, instanceOf(UpdateChangeRecord.class)));
        assertEquals(SKOS.DEFINITION, ((UpdateChangeRecord) result.get(0)).getChangedAttribute().toString());
        assertEquals(SKOS.PREF_LABEL, ((UpdateChangeRecord) result.get(1)).getChangedAttribute().toString());
    }

    @Test
    void findAllReturnsEmptyListForUnknownAsset() {
        enableRdfsInference(em);
        final List<AbstractChangeRecord> result = sut.findAll(Generator.generateTermWithId());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void persistSavesChangeRecordWithLiteralValueChange() {
        final UpdateChangeRecord record = generateUpdateRecord(Instant.now(), Generator.generateUri());
        record.setOriginalValue(Collections.singleton("original value"));
        record.setNewValue(Collections.singleton("new value"));
        transactional(() -> sut.persist(record, vocabulary));

        final UpdateChangeRecord result = em.find(UpdateChangeRecord.class, record.getUri());
        assertNotNull(result);
        assertEquals(record.getOriginalValue(), result.getOriginalValue());
        assertEquals(record.getNewValue(), result.getNewValue());
    }

    @Test
    void persistSavesRecordWithReferenceValueChange() {
        final UpdateChangeRecord record = generateUpdateRecord(Instant.now(), Generator.generateUri());
        record.setNewValue(Collections.singleton(Generator.generateUri()));
        transactional(() -> sut.persist(record, vocabulary));

        final UpdateChangeRecord result = em.find(UpdateChangeRecord.class, record.getUri());
        assertNotNull(result);
        assertNull(result.getOriginalValue());
        assertEquals(record.getNewValue(), result.getNewValue());
    }

    @Test
    void supportsWorkingWithMultilingualAttributes() {
        enableRdfsInference(em);
        final UpdateChangeRecord record = generateUpdateRecord(Instant.now(), vocabulary.getUri());
        final MultilingualString original = MultilingualString.create("Test term", "en");
        final MultilingualString newValue = new MultilingualString(original.getValue());
        newValue.set("cs", "Testovací pojem");
        record.setOriginalValue(Collections.singleton(original));
        record.setNewValue(Collections.singleton(newValue));
        transactional(() -> sut.persist(record, vocabulary));

        final List<AbstractChangeRecord> result = sut.findAll(vocabulary);
        assertEquals(1, result.size());
        assertThat(result.get(0), instanceOf(UpdateChangeRecord.class));
        final UpdateChangeRecord updateRecord = (UpdateChangeRecord) result.get(0);
        assertEquals(Collections.singleton(original), updateRecord.getOriginalValue());
        assertEquals(Collections.singleton(newValue), consolidateMultilingualStrings(updateRecord.getNewValue()));
    }

    private Set<Object> consolidateMultilingualStrings(Set<Object> source) {
        final List<MultilingualString> target = new ArrayList<>();
        for (Object src : source) {
            assert src instanceof MultilingualString;
            final MultilingualString ms = (MultilingualString) src;
            if (target.isEmpty() || ms.getLanguages().size() > 1) {
                target.add(ms);
                continue;
            }
            final String lang = ms.getLanguages().iterator().next();
            final Optional<MultilingualString> existing = target.stream().filter(e -> !e.contains(lang)).findFirst();
            if (existing.isPresent()) {
                existing.get().set(lang, ms.get(lang));
            } else {
                target.add(ms);
            }
        }
        return new HashSet<>(target);
    }
}
