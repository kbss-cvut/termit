package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.WorkspaceService;
import cz.cvut.kbss.termit.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceController.class);

    private final IdentifierResolver idResolver;

    private final WorkspaceService workspaceService;


    @Autowired
    public WorkspaceController(IdentifierResolver idResolver, WorkspaceService workspaceService) {
        this.idResolver = idResolver;
        this.workspaceService = workspaceService;
    }

    @GetMapping(value = "/current", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Workspace getCurrent() {
        return workspaceService.getCurrentWorkspace();
    }

    @PutMapping(value = "/{fragment}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Workspace loadWorkspace(@PathVariable String fragment,
                                   @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final URI uri = idResolver.resolveIdentifier(namespace, fragment);
        final Workspace workspace = workspaceService.loadWorkspace(uri);
        LOG.debug("Successfully loaded workspace {}.", workspace);
        return workspace;
    }
}
