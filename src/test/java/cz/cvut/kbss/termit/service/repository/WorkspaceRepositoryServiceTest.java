package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.workspace.WorkspaceMetadataCache;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspaceMetadataCache workspaceMetadataCache;

    @Autowired
    private WorkspaceRepositoryService sut;

    @BeforeEach
    void setUp() {
        Mockito.reset(workspaceStore, workspaceMetadataCache);
    }

    @Test
    void loadWorkspaceByIdRetrievesWorkspaceFromRepository() {
        final Workspace expected = generateWorkspace();
        final Workspace result = sut.loadWorkspace(expected.getUri());
        assertEquals(expected, result);
    }

    private Workspace generateWorkspace() {
        final Workspace workspace = WorkspaceGenerator.generateWorkspace();
        transactional(() -> em.persist(workspace, new EntityDescriptor(workspace.getUri())));
        return workspace;
    }

    @Test
    void loadWorkspaceByIdThrowsNotFoundExceptionWhenWorkspaceDoesNotExist() {
        assertThrows(NotFoundException.class, () -> sut.loadWorkspace(Generator.generateUri()));
    }

    @Test
    void loadWorkspaceByIdStoresLoadedWorkspaceIdInWorkspaceStore() {
        final Workspace expected = generateWorkspace();
        sut.loadWorkspace(expected.getUri());
        assertEquals(expected.getUri(), workspaceStore.getCurrentWorkspace());
    }

    @Test
    void loadWorkspaceByIdStoresLoadedWorkspaceMetadataInCache() {
        final Workspace expected = generateWorkspace();
        sut.loadWorkspace(expected.getUri());
        final Workspace result = workspaceMetadataCache.getWorkspace(expected.getUri());
        assertEquals(expected, result);
    }

    @Test
    void loadWorkspaceLoadsMetadataAboutWorkspaceVocabulariesAndTheirContexts() {
        final Workspace workspace = generateWorkspace();
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        transactional(() -> {
            vocabularies.forEach(v -> em.persist(v, new EntityDescriptor(v.getUri())));
            addWorkspaceReference(vocabularies, workspace);
        });


        sut.loadWorkspace(workspace.getUri());
        final WorkspaceMetadata result = workspaceMetadataCache.getWorkspaceMetadata(workspace.getUri());
        assertNotNull(result);
        vocabularies.forEach(v -> assertThat(result.getVocabularies(), hasKey(v.getUri())));
    }

    private void addWorkspaceReference(Collection<Vocabulary> vocabularies, Workspace workspace) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            conn.add(WorkspaceGenerator.generateWorkspaceReferences(vocabularies, workspace));
            conn.commit();
        }
    }
}
