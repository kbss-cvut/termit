package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping(TermOccurrenceController.PATH)
public class TermOccurrenceController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(TermOccurrenceController.class);

    /**
     * URL path to this controller's endpoints.
     */
    public static final String PATH = "/occurrence";


    private final TermOccurrenceService occurrenceService;

    public TermOccurrenceController(IdentifierResolver idResolver, Configuration config,
                                    TermOccurrenceService occurrenceService) {
        super(idResolver, config);
        this.occurrenceService = occurrenceService;
    }

    @PutMapping(value = "/{normalizedName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void approveOccurrence(@PathVariable String normalizedName,
                                  @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final URI identifier = idResolver.resolveIdentifier(namespace, normalizedName);

        occurrenceService.approve(occurrenceService.getRequiredReference(identifier));
        LOG.debug("Occurrence with identifier <{}> approved.", identifier);
    }

    @DeleteMapping(value = "/{normalizedName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void removeOccurrence(@PathVariable String normalizedName,
                                 @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final URI identifier = idResolver.resolveIdentifier(namespace, normalizedName);
        occurrenceService.remove(occurrenceService.getRequiredReference(identifier));
        LOG.debug("Occurrence with identifier <{}> removed.", identifier);
    }
}
