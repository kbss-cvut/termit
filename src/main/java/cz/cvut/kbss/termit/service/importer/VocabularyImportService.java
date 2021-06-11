package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
     * @param rename if true, IRIs can be modified to avoid collisions with existing data.
     * @param vocabularyIri IRI of an existing vocabulary.
     * @param file File containing the vocabulary to import. Can be a file containing RDF, or a ZIP containing multiple
     *             RDF files
     * @return {@code Vocabulary} object containing metadata of the imported vocabulary
     */
    @Transactional
    public Vocabulary importVocabulary(boolean rename, URI vocabularyIri, MultipartFile file) {
        Objects.requireNonNull(file);
        try {
            return getSKOSImporter().importVocabulary( rename, vocabularyIri, file.getContentType(), file.getInputStream());
        } catch (VocabularyImportException e) {
            throw e;
        } catch (Exception e) {
            throw new VocabularyImportException("Unable to import vocabulary, because of: " + e.getMessage());
        }
    }
}
