package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.SnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cz.cvut.kbss.termit.util.Constants.QueryParams.NAMESPACE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SnapshotControllerTest extends BaseControllerTestRunner {

    @Mock
    private SnapshotService snapshotService;

    @Mock
    private IdentifierResolver idResolver;

    @InjectMocks
    private SnapshotController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void removeSnapshotLoadsSnapshotWithSpecifiedIdAndRemovesIt() throws Exception {
        final Snapshot snapshot = Generator.generateSnapshot(Generator.generateVocabularyWithId());
        final String localName = IdentifierResolver.extractIdentifierFragment(snapshot.getUri());
        final String namespace = IdentifierResolver.extractIdentifierNamespace(snapshot.getUri());
        when(idResolver.resolveIdentifier(namespace, localName)).thenReturn(snapshot.getUri());
        when(snapshotService.findRequired(snapshot.getUri())).thenReturn(snapshot);
        mockMvc.perform(delete(SnapshotController.PATH + "/" + localName).queryParam(NAMESPACE, namespace))
               .andExpect(status().isNoContent());

        verify(snapshotService).findRequired(snapshot.getUri());
        verify(snapshotService).remove(snapshot);
    }
}
