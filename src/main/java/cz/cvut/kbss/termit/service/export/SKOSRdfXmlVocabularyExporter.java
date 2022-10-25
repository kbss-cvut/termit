package cz.cvut.kbss.termit.service.export;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Exports vocabulary glossary in a SKOS-compatible format serialized as RDF/XML.
 */
@Service("skos-rdfxml")
public class SKOSRdfXmlVocabularyExporter extends SKOSVocabularyExporter {

    public SKOSRdfXmlVocabularyExporter(ApplicationContext context) {
        super(context);
    }

    @Override
    protected ExportFormat exportFormat() {
        return ExportFormat.RDF_XML;
    }
}
