package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSExporter;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cz.cvut.kbss.termit.util.Constants.Turtle;

/**
 * Exports vocabulary glossary in a SKOS-compatible format.
 */
@Service("skos")
public class SKOSVocabularyExporter implements VocabularyExporter {

    private static final Logger LOG = LoggerFactory.getLogger(SKOSVocabularyExporter.class);

    private final ApplicationContext context;

    @Autowired
    public SKOSVocabularyExporter(ApplicationContext context) {
        this.context = context;
    }

    /**
     * This method ensures new instances of the prototype-scoped bean are returned on every call.
     */
    private SKOSExporter getSKOSExporter() {
        return context.getBean(SKOSExporter.class);
    }

    @Override
    @Transactional
    public TypeAwareResource exportVocabularyGlossary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        LOG.debug("Exporting glossary of vocabulary {} to SKOS.", vocabulary);
        final SKOSExporter skosExporter = getSKOSExporter();
        LOG.trace("Exporting glossary.");
        skosExporter.exportGlossaryInstance(vocabulary);
        LOG.trace("Exporting terms.");
        skosExporter.exportGlossaryTerms(vocabulary);
        return new TypeAwareByteArrayResource(skosExporter.exportAsTtl(), Turtle.MEDIA_TYPE, Turtle.FILE_EXTENSION);
    }

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(Turtle.MEDIA_TYPE, mediaType);
    }
}
