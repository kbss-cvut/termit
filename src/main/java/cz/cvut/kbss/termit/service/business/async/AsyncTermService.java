package cz.cvut.kbss.termit.service.business.async;

import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.TermService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Provides asynchronous processing of term-related tasks.
 */
@Service
public class AsyncTermService {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncTermService.class);

    private final TermService termService;

    public AsyncTermService(TermService termService) {
        this.termService = termService;
    }

    /**
     * Gets a list of all terms in the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose terms to retrieve. A reference is sufficient
     * @return List of vocabulary term DTOs
     */
    public List<TermDto> findAll(Vocabulary vocabulary) {
        return termService.findAll(vocabulary);
    }

    /**
     * Asynchronously runs text analysis on the definitions of all the specified terms.
     * <p>
     * The analysis calls are executed in a sequence, but this method itself is executed asynchronously.
     *
     * @param termsWithContexts Map of terms to vocabulary context identifiers they belong to
     */
    @Async
    public void asyncAnalyzeTermDefinitions(Map<? extends AbstractTerm, URI> termsWithContexts) {
        LOG.trace("Asynchronously analyzing definitions of {} terms.", termsWithContexts.size());
        termsWithContexts.forEach(termService::analyzeTermDefinition);
    }
}
