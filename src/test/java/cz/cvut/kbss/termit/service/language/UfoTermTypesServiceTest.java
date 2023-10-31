package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UfoTermTypesServiceTest {

    @Mock
    private ClassPathResource languageTtlUrl;

    @InjectMocks
    private UfoTermTypesService sut;

    @Test
    void getTypesForBasicLanguage() throws IOException {
        final URL url = ClassLoader.getSystemResource("languages/types.ttl");
        when(languageTtlUrl.getInputStream()).thenReturn(url.openStream());
        List<Term> result = sut.getTypes();
        assertEquals(10, result.size());
    }

    @Test
    void getTypesSupportsTopDownTermHierarchy() throws Exception {
        final URL url = ClassLoader.getSystemResource("languages/types.ttl");
        when(languageTtlUrl.getInputStream()).thenReturn(url.openStream());
        List<Term> result = sut.getTypes();
        final Optional<Term> individual = result.stream()
                                                .filter(t -> t.getUri().toString().equals(Vocabulary.s_c_individual))
                                                .findAny();
        assertTrue(individual.isPresent());
        assertFalse(individual.get().getSubTerms().isEmpty());
    }

    @Test
    void getTypesSupportsBottomUpTermHierarchy() throws Exception {
        final URL url = ClassLoader.getSystemResource("languages/bottomUpTypes.ttl");
        when(languageTtlUrl.getInputStream()).thenReturn(url.openStream());
        List<Term> result = sut.getTypes();
        final Optional<Term> individual = result.stream()
                                                .filter(t -> t.getUri().toString().equals(Vocabulary.s_c_individual))
                                                .findAny();
        assertTrue(individual.isPresent());
        assertFalse(individual.get().getSubTerms().isEmpty());
    }
}
