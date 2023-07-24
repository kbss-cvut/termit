package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.termit.model.Term;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URL;
import java.util.List;

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
        final URL url = ClassLoader.getSystemResource("languages/language.ttl");
        when(languageTtlUrl.getURL()).thenReturn(url);
        List<Term> result = sut.getTypes();
        assertEquals(8, result.size());
    }
}
