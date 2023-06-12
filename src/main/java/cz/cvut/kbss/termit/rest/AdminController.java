package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.jmx.AppAdminBean;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Administration", description = "Application administration API")
@RestController
@RequestMapping("/admin")
@Profile("!test")
public class AdminController {

    private static final Logger LOG = LoggerFactory.getLogger(AdminController.class);

    private final AppAdminBean adminBean;

    @Autowired
    public AdminController(AppAdminBean adminBean) {
        this.adminBean = adminBean;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Evicts all application caches. Useful, for example, when data were modified manually in the repository.")
    @ApiResponse(responseCode = "204", description = "Caches evicted.")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @DeleteMapping("/cache")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invalidateCaches() {
        LOG.debug("Cache invalidation request received from client.");
        adminBean.invalidateCaches();
    }
}
