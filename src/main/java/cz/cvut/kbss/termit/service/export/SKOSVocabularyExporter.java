package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSExporter;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Exports vocabulary glossary in a SKOS-compatible format.
 */
public abstract class SKOSVocabularyExporter implements VocabularyExporter {

    private static final Logger LOG = LoggerFactory.getLogger(SKOSVocabularyExporter.class);

    private final ApplicationContext context;

    protected SKOSVocabularyExporter(ApplicationContext context) {
        this.context = context;
    }

    /**
     * This method ensures new instances of the prototype-scoped bean are returned on every call.
     */
    private SKOSExporter getSKOSExporter() {
        return context.getBean(SKOSExporter.class);
    }

    @Override
    @Transactional(readOnly = true)
    public TypeAwareResource exportGlossary(Vocabulary vocabulary, ExportConfig config) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(config);
        LOG.debug("Exporting glossary of vocabulary {} as {}.", vocabulary, config.getType());
        final SKOSExporter skosExporter = getSKOSExporter();
        switch (config.getType()) {
            case SKOS:
                skosExporter.exportGlossary(vocabulary);
                break;
            case SKOS_FULL:
                skosExporter.exportFullGlossary(vocabulary);
                break;
            case SKOS_WITH_REFERENCES:
                skosExporter.exportGlossaryWithReferences(vocabulary, config.getReferenceProperties());
                break;
            case SKOS_FULL_WITH_REFERENCES:
                skosExporter.exportFullGlossaryWithReferences(vocabulary, config.getReferenceProperties());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported export type " + config.getType());
        }
        final TypeAwareResource res = new TypeAwareByteArrayResource(skosExporter.exportAs(exportFormat()),
                                                                     exportFormat().getMediaType(),
                                                                     exportFormat().getFileExtension());
        LOG.trace("Export finished successfully.");
        return res;
    }

    protected abstract ExportFormat exportFormat();

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(exportFormat().getMediaType(), mediaType);
    }
}
