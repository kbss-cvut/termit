package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.workspace.WorkspaceMetadataCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.util.Collections;

import static cz.cvut.kbss.termit.util.Constants.DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class ChangeTrackingContextResolverTest {

    public static final URI CHANGE_TRACKING_CTX = Generator.generateUri();

    private WorkspaceMetadata metadata;

    @Mock
    private WorkspaceMetadataCache workspaceMetadataCache;

    @InjectMocks
    private ChangeTrackingContextResolver sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        final Workspace ws = Generator.generateWorkspace();
        this.metadata = new WorkspaceMetadata(ws);
        when(workspaceMetadataCache.getCurrentWorkspace()).thenReturn(ws);
        when(workspaceMetadataCache.getCurrentWorkspaceMetadata()).thenReturn(metadata);
    }

    @Test
    void resolveChangeTrackingContextReturnsWorkspaceVocabularyChangeTrackingContextForVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        metadata.setVocabularies(Collections.singletonMap(vocabulary.getUri(),
                new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), CHANGE_TRACKING_CTX)));
        final URI result = sut.resolveChangeTrackingContext(vocabulary);
        assertNotNull(result);
        assertEquals(CHANGE_TRACKING_CTX, result);
    }

    @Test
    void resolveChangeTrackingContextReturnsWorkspaceVocabularyChangeTrackingContextForTerm() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        metadata.setVocabularies(Collections.singletonMap(vocabulary.getUri(),
                new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), CHANGE_TRACKING_CTX)));
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        final URI result = sut.resolveChangeTrackingContext(term);
        assertNotNull(result);
        assertEquals(CHANGE_TRACKING_CTX, result);
    }

    @Test
    void resolveChangeTrackingContextReturnsResourceIdentifierWithTrackingExtensionForResource() {
        final Resource resource = Generator.generateResourceWithId();
        final URI result = sut.resolveChangeTrackingContext(resource);
        assertNotNull(result);
        assertEquals(resource.getUri().toString().concat(DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION), result.toString());
    }
}
