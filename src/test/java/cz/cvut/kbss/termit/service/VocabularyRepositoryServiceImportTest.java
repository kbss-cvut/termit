package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyRepositoryServiceImportTest {

    @Mock
    private SKOSImporter importer;

    @Mock
    private ApplicationContext context;

    @Mock
    private Configuration configuration;

    @InjectMocks
    private VocabularyRepositoryService sut;

    @BeforeEach
    void setUp() {
        when(context.getBean(SKOSImporter.class)).thenReturn(importer);
    }

    @Test
    void passesInputStreamFromProvidedInputFileToImporter() throws IOException {
        final MultipartFile input = new MockMultipartFile("vocabulary.ttl", "vocabulary.ttl",
                Constants.Turtle.MEDIA_TYPE, Environment.loadFile("data/test-vocabulary.ttl"));
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(importer.importVocabulary(anyBoolean(), any(),  any(), any(), any())).thenReturn(vocabulary);
        final Vocabulary result = sut.importVocabulary(false,vocabulary.getUri(),input);
        final ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(importer).importVocabulary(eq(false), eq(vocabulary.getUri()), eq(Constants.Turtle.MEDIA_TYPE), any(), captor.capture());
        assertNotNull(captor.getValue());
        assertEquals(vocabulary, result);
    }
}
