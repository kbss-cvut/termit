package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cz.cvut.kbss.termit.environment.config.WorkspaceTestConfig.DEFAULT_VOCABULARY_CTX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

class WorkspaceBasedAssetDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private WorkspaceMetadataProvider workspaceMetadataProvider;

    @Autowired
    private VocabularyDao sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findRecentlyEditedReturnsRecentChangesFromCurrentWorkspaceOnly() {
        enableRdfsInference(em);
        final List<AbstractChangeRecord> records = generateChangeRecords();

        final List<RecentlyModifiedAsset> result = sut.findLastEdited(Constants.DEFAULT_PAGE_SIZE);
        assertEquals(records.size(), result.size());
        records.forEach(r -> assertTrue(result.stream().anyMatch(rma -> rma.getUri().equals(r.getChangedEntity()))));
    }

    private List<AbstractChangeRecord> generateChangeRecords() {
        final List<AbstractChangeRecord> matching = new ArrayList<>();
        transactional(() -> {
            for (int i = 0; i < 10; i++) {
                final Vocabulary v = Generator.generateVocabularyWithId();
                em.persist(v, descriptorFactory.vocabularyDescriptor(v));
                final PersistChangeRecord record = Generator.generatePersistChange(v);
                if (Generator.randomBoolean()) {
                    final WorkspaceMetadata wsMetadata = workspaceMetadataProvider.getCurrentWorkspaceMetadata();
                    doReturn(Collections.singleton(DEFAULT_VOCABULARY_CTX)).when(wsMetadata)
                                                                           .getChangeTrackingContexts();
                    em.persist(record, new EntityDescriptor(
                            workspaceMetadataProvider.getCurrentWorkspaceMetadata().getChangeTrackingContexts()
                                                     .iterator()
                                                     .next())
                            .addAttributeContext(descriptorFactory.fieldSpec(PersistChangeRecord.class, "author"),
                                    null));
                    matching.add(record);
                } else {
                    em.persist(record, new EntityDescriptor(Generator.generateUri())
                            .addAttributeContext(descriptorFactory.fieldSpec(PersistChangeRecord.class, "author"),
                                    null));
                }
            }
        });
        return matching;
    }

    @Test
    void findLastEditedByReturnsRecentChangesByUserFromCurrentWorkspaceOnly() {
        enableRdfsInference(em);
        final List<AbstractChangeRecord> records = generateChangeRecords();

        final List<RecentlyModifiedAsset> result = sut.findLastEditedBy(author, Constants.DEFAULT_PAGE_SIZE);
        assertEquals(records.size(), result.size());
        records.forEach(r -> assertTrue(result.stream().anyMatch(rma -> rma.getUri().equals(r.getChangedEntity()) && author.equals(rma.getEditor()))));
    }
}
