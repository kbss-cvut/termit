package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import java.io.IOException;
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
     * @param vocabularyIri IRI of the vocabulary to be created.
     * @param file File containing the vocabulary to import. Can be a file containing RDF, or a ZIP containing multiple
     *             RDF files
     * @return {@code Vocabulary} object containing metadata of the imported vocabulary
     */
    @Transactional
    public Vocabulary importVocabulary(String vocabularyIri, MultipartFile file) {
        Objects.requireNonNull(file);
        try {
            return getSKOSImporter().importVocabulary(vocabularyIri, file.getContentType(), file.getInputStream());
        } catch (IOException e) {
            throw new TermItException("Unable to read file with vocabulary to import.", e);
        }
    }
}
