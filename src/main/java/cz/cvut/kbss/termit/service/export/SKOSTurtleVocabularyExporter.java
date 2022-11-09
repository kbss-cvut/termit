package cz.cvut.kbss.termit.service.export;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Exports vocabulary glossary in a SKOS-compatible format serialized as Turtle.
 */
@Service("skos-turtle")
public class SKOSTurtleVocabularyExporter extends SKOSVocabularyExporter {

    @Autowired
    public SKOSTurtleVocabularyExporter(ApplicationContext context) {
        super(context);
    }

    @Override
    protected ExportFormat exportFormat() {
        return ExportFormat.TURTLE;
    }
}
