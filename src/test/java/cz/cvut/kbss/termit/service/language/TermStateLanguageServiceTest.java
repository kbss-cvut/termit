package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.net.URL;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermStateLanguageServiceTest {

    @Mock
    private ClassPathResource statesFile;

    @InjectMocks
    private TermStateLanguageService sut;

    @Test
    void getTermStatesLoadsTermStatesFromConfigFile() throws Exception {
        final URL url = ClassLoader.getSystemResource("languages/states.ttl");
        when(statesFile.getInputStream()).thenReturn(url.openStream());

        final List<RdfsResource> result = sut.getTermStates();
        assertEquals(3, result.size());
        result.forEach(r -> {
            assertNotNull(r.getLabel());
            assertThat(r.getLabel().getValue(), hasKey("cs"));
            assertThat(r.getLabel().getValue(), hasKey("en"));
            assertNotNull(r.getComment());
            assertThat(r.getComment().getValue(), hasKey("cs"));
            assertThat(r.getComment().getValue(), hasKey("en"));
        });
    }

    @Test
    void getTermStatesLoadsInfoAboutInitialAndTerminalState() throws Exception {
        final URL url = ClassLoader.getSystemResource("languages/states.ttl");
        when(statesFile.getInputStream()).thenReturn(url.openStream());

        final List<RdfsResource> result = sut.getTermStates();
        assertTrue(result.stream().anyMatch(r -> r.hasType(Vocabulary.s_c_uvodni_stav_pojmu)));
        assertTrue(result.stream().anyMatch(r -> r.hasType(Vocabulary.s_c_koncovy_stav_pojmu)));
    }
}
