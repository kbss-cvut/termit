package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.WorkspaceMetadataCache;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpSession;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.service.repository.WorkspaceRepositoryService.WORKSPACE_SESSION_ATT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private HttpSession session;

    @Autowired
    private WorkspaceMetadataCache workspaceMetadataCache;

    @Autowired
    private WorkspaceRepositoryService sut;

    @Test
    void loadWorkspaceByIdRetrievesWorkspaceFromRepository() {
        final Workspace expected = generateWorkspace();
        final Workspace result = sut.loadWorkspace(expected.getUri());
        assertEquals(expected, result);
    }

    private Workspace generateWorkspace() {
        final Workspace workspace = new Workspace();
        workspace.setUri(Generator.generateUri());
        workspace.setLabel("Test workspace " + Generator.randomInt(0, 1000));
        transactional(() -> em.persist(workspace, new EntityDescriptor(workspace.getUri())));
        return workspace;
    }

    @Test
    void loadWorkspaceByIdThrowsNotFoundExceptionWhenWorkspaceDoesNotExist() {
        assertThrows(NotFoundException.class, () -> sut.loadWorkspace(Generator.generateUri()));
    }

    @Test
    void loadWorkspaceByIdStoresLoadedWorkspaceIdInSession() {
        final Workspace expected = generateWorkspace();
        sut.loadWorkspace(expected.getUri());
        assertEquals(expected.getUri(), session.getAttribute(WORKSPACE_SESSION_ATT));
    }

    @Test
    void loadWorkspaceByIdStoresLoadedWorkspaceMetadataInCache() {
        final Workspace expected = generateWorkspace();
        sut.loadWorkspace(expected.getUri());
        final Workspace result = workspaceMetadataCache.getWorkspace(expected.getUri());
        assertEquals(expected, result);
    }

    @Test
    void getCurrentWorkspaceRetrievesCurrentWorkspaceFromCacheBasedOnIdentifierStoredInSession() {
        final Workspace expected = generateWorkspace();
        session.setAttribute(WORKSPACE_SESSION_ATT, expected.getUri());
        workspaceMetadataCache.putWorkspace(new WorkspaceMetadata(expected));
        assertEquals(expected, sut.getCurrentWorkspace());
    }

    @Test
    void getCurrentWorkspaceThrowsWorkspaceNotSetExceptionWhenNoWorkspaceIsSelected() {
        assertNull(session.getAttribute(WORKSPACE_SESSION_ATT));
        assertThrows(WorkspaceNotSetException.class, () -> sut.getCurrentWorkspace());
    }

    @Test
    void loadWorkspaceLoadsMetadataAboutWorkspaceVocabulariesAndTheirContexts() {
        final Workspace workspace = generateWorkspace();
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        addWorkspaceReference(vocabularies, workspace);


        sut.loadWorkspace(workspace.getUri());
        final WorkspaceMetadata result = workspaceMetadataCache.getWorkspaceMetadata(workspace.getUri());
        assertNotNull(result);
        vocabularies.forEach(v -> assertThat(result.getVocabularies(), hasKey(v.getUri())));
    }

    private void addWorkspaceReference(Collection<Vocabulary> vocabularies, Workspace workspace) {
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                conn.begin();
                conn.add(Generator.generateWorkspaceReferences(vocabularies, workspace));
                conn.commit();
            }
        });
    }
}
