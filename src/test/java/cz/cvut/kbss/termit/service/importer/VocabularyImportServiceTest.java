package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyImportServiceTest {

    @Mock
    private SKOSImporter importer;

    @Mock
    private ApplicationContext context;

    @InjectMocks
    private VocabularyImportService sut;

    @BeforeEach
    void setUp() {
        when(context.getBean(SKOSImporter.class)).thenReturn(importer);
    }

    @Test
    void passesInputStreamFromProvidedInputFileToImporter() throws IOException {
        final MultipartFile input = new MockMultipartFile("vocabulary.ttl", "vocabulary.ttl",
                Constants.Turtle.MEDIA_TYPE, Environment.loadFile("vocabularies/ipr-glossaries.ttl"));
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(importer.importVocabulary(any(), any())).thenReturn(vocabulary);
        final Vocabulary result = sut.importVocabulary(input);
        final ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(importer).importVocabulary(eq(Constants.Turtle.MEDIA_TYPE), captor.capture());
        assertNotNull(captor.getValue());
        assertEquals(vocabulary, result);
    }

    @Test
    void extractsIndividualFilesFromZipAndPassesTheirInputStreamsToImporter() throws IOException {
        final MultipartFile input = new MockMultipartFile("vocabulary.zip", "vocabulary.zip",
                Constants.ZIP_MEDIA_TYPE, generateZipFile().toByteArray());
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(importer.importVocabulary(anyString(), any(InputStream.class))).thenReturn(vocabulary);
        sut.importVocabulary(input);
        final ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(importer).importVocabulary(eq(Constants.Turtle.MEDIA_TYPE), captor.capture());
        assertEquals(2, captor.getAllValues().size());
    }

    private ByteArrayOutputStream generateZipFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            ZipEntry entry = new ZipEntry("vocabulary.ttl");

            zos.putNextEntry(entry);
            try (InputStream is = Environment.loadFile("vocabularies/ipr-glossaries.ttl")) {
                byte[] bytes = new byte[1024];
                int length;
                while ((length = is.read(bytes)) >= 0) {
                    zos.write(bytes, 0, length);
                }
            }
            zos.closeEntry();

            entry = new ZipEntry("vocabulary2.ttl");
            zos.putNextEntry(entry);
            try (InputStream is = Environment.loadFile("vocabularies/ipr-glossaries.ttl")) {
                byte[] bytes = new byte[1024];
                int length;
                while ((length = is.read(bytes)) >= 0) {
                    zos.write(bytes, 0, length);
                }
            }
            zos.closeEntry();
        }
        return baos;
    }
}
