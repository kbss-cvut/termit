package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Allows to import a vocabulary from RDF.
 */
@Service
public class VocabularyImportService {

    private final ApplicationContext context;

    @Autowired
    public VocabularyImportService(ApplicationContext context) {
        this.context = context;
    }

    /**
     * This method ensures new instances of the prototype-scoped bean are returned on every call.
     */
    private SKOSImporter getSKOSImporter() {
        return context.getBean(SKOSImporter.class);
    }

    /**
     * Import vocabulary from the specified file.
     *
     * @param file File containing the vocabulary to import. Can be a file containing RDF, or a ZIP containing multiple
     *             RDF files
     * @return {@code Vocabulary} object containing metadata of the imported vocabulary
     */
    @Transactional
    public Vocabulary importVocabulary(MultipartFile file) {
        Objects.requireNonNull(file);
        try {
            if (Constants.ZIP_MEDIA_TYPE.equals(file.getContentType())) {
                return importFromZipFile(file);
            } else {
                return getSKOSImporter().importVocabulary(file.getContentType(), file.getInputStream());
            }
        } catch (IOException e) {
            throw new TermItException("Unable to read file with vocabulary to import.", e);
        }
    }

    private Vocabulary importFromZipFile(MultipartFile file) throws IOException {
        final Path tempFile = Files.createTempFile("vocabulary-import", ".zip");
        try {
            file.transferTo(tempFile);
            try (final ZipFile zipFile = new ZipFile(tempFile.toFile())) {
                final List<InputStream> lst = new ArrayList<>();
                final Enumeration<? extends ZipEntry> en = zipFile.entries();
                String mediaType = null;
                while (en.hasMoreElements()) {
                    final ZipEntry ze = en.nextElement();
                    lst.add(zipFile.getInputStream(ze));
                    if (mediaType == null) {
                        mediaType = SKOSImporter.guessMediaType(ze.getName());
                    }
                }
                return getSKOSImporter().importVocabulary(mediaType, lst.toArray(new InputStream[0]));
            }
        } finally {
            Files.delete(tempFile);
        }
    }
}
