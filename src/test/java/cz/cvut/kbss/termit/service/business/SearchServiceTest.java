package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    @Mock
    private SearchDao searchDao;

    @Mock
    private WorkspaceMetadataProvider wsMetadataCache;

    @InjectMocks
    private SearchService sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void fullTextSearchExtractsContextsForSearchingFromCurrentWorkspace() {
        final WorkspaceMetadata current = new WorkspaceMetadata(Generator.generateWorkspace());
        final VocabularyInfo vocInfo = new VocabularyInfo(Generator.generateUri(), Generator.generateUri(),
                Generator.generateUri());
        current.getVocabularies().put(vocInfo.getUri(), vocInfo);
        when(wsMetadataCache.getCurrentWorkspaceMetadata()).thenReturn(current);
        sut.fullTextSearch("test");
        verify(searchDao).fullTextSearch("test", current.getVocabularyContexts());
    }
}
