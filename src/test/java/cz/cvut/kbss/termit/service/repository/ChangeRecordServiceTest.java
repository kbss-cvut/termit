package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
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
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ChangeRecordService sut;

    @Autowired
    private Configuration config;

    private User author;

    private Vocabulary asset;

    private String contextExtension;

    @BeforeEach
    void setUp() {
        this.contextExtension = config.getChangetracking().getContext().getExtension();
        this.asset = Generator.generateVocabularyWithId();
        Glossary glossary = new Glossary();
        glossary.setUri(Generator.generateUri());
        asset.setGlossary(glossary);
        this.author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> {
            em.persist(author);
            em.persist(asset, descriptorFactory.vocabularyDescriptor(asset));
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
        Descriptor descriptor = persistDescriptor(URI.create(asset.getUri().toString().concat(contextExtension)));
        final List<AbstractChangeRecord> records = IntStream.range(0, 5).mapToObj(i -> {
            final UpdateChangeRecord r = new UpdateChangeRecord(asset);
            r.setChangedAttribute(URI.create(DC.Terms.TITLE));
            r.setAuthor(author);
            r.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis() - i * 10000L));
            return r;
        }).collect(Collectors.toList());
        transactional(() -> records.forEach(record -> em.persist(record, descriptor)));
        return records;
    }

    private Descriptor persistDescriptor(URI context) {
        final EntityDescriptor descriptor = new EntityDescriptor(context);
        descriptor.addAttributeDescriptor(em.getMetamodel().entity(AbstractChangeRecord.class).getAttribute("author"),
                new EntityDescriptor());
        return descriptor;
    }
}
