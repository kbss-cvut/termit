package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.SnapshotService;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static cz.cvut.kbss.termit.util.Constants.QueryParams.NAMESPACE;

@RestController
@RequestMapping(SnapshotController.PATH)
public class SnapshotController extends BaseController {

    public static final String PATH = "/snapshots";

    private final SnapshotService snapshotService;

    public SnapshotController(IdentifierResolver idResolver, Configuration config, SnapshotService snapshotService) {
        super(idResolver, config);
        this.snapshotService = snapshotService;
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @DeleteMapping(value = "/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSnapshot(@PathVariable String localName, @RequestParam(name = NAMESPACE) String namespace) {
        final URI id = idResolver.resolveIdentifier(namespace, localName);
        snapshotService.remove(snapshotService.findRequired(id));
    }
}
