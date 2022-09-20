package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.rest.WorkspaceController.PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest extends BaseControllerTestRunner {

    @Mock
    private WorkspaceService workspaceService;

    @InjectMocks
    private WorkspaceController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void openForEditingPassesSpecifiedContextsToServiceToOpenForEditing() throws Exception {
        final Set<URI> contexts = IntStream.range(0, 5).mapToObj(i -> Generator.generateUri())
                                           .collect(Collectors.toSet());
        mockMvc.perform(put(PATH).content(toJson(contexts)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isNoContent());
        final ArgumentCaptor<Collection<URI>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(workspaceService).openForEditing(captor.capture());
        assertEquals(contexts.size(), captor.getValue().size());
        assertThat(captor.getValue(), hasItems(contexts.toArray(new URI[]{})));
    }
}
