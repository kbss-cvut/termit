package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeRecordServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private ChangeRecordService sut;

    private User author;

    private Vocabulary asset;

    @BeforeEach
    void setUp() {
        this.asset = Generator.generateVocabularyWithId();
        this.author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> {
            em.persist(author);
            em.persist(asset, DescriptorFactory.vocabularyDescriptor(asset));
        });
    }

    @Test
    void getChangesReturnsChangesForSpecifiedAsset() {
        enableRdfsInference(em);
        final List<AbstractChangeRecord> records = generateChanges();
        records.sort(Comparator.comparing(AbstractChangeRecord::getTimestamp).reversed());

        final List<AbstractChangeRecord> result = sut.getChanges(asset);
        assertEquals(records, result);
    }

    private List<AbstractChangeRecord> generateChanges() {
        final List<AbstractChangeRecord> records = IntStream.range(0, 5).mapToObj(i -> {
            final UpdateChangeRecord r = new UpdateChangeRecord(asset);
            r.setChangedAttribute(URI.create(DC.Terms.TITLE));
            r.setAuthor(author);
            r.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis() - i * 10000));
            return r;
        }).collect(Collectors.toList());
        transactional(() -> records.forEach(em::persist));
        return records;
    }
}
