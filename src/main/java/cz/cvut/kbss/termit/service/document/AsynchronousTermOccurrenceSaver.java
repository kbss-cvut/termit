package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Saves term occurrences asynchronously.
 */
@Primary
@Service
@Profile("!test")
public class AsynchronousTermOccurrenceSaver implements TermOccurrenceSaver {

    private static final Logger LOG = LoggerFactory.getLogger(AsynchronousTermOccurrenceSaver.class);

    private final SynchronousTermOccurrenceSaver synchronousSaver;

    public AsynchronousTermOccurrenceSaver(SynchronousTermOccurrenceSaver synchronousSaver) {
        this.synchronousSaver = synchronousSaver;
    }

    @Async
    @Override
    public void saveOccurrences(List<TermOccurrence> occurrences, Asset<?> source) {
        LOG.debug("Asynchronously saving term occurrences for asset {}.", source);
        synchronousSaver.saveOccurrences(occurrences, source);
        LOG.trace("Finished saving term occurrences for asset {}.", source);
    }

    @Override
    public List<TermOccurrence> getExistingOccurrences(Asset<?> source) {
        return synchronousSaver.getExistingOccurrences(source);
    }
}
