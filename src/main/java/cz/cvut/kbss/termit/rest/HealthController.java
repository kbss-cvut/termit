package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.rest.dto.HealthInfo;
import cz.cvut.kbss.termit.service.jmx.AppAdminBean;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "Application health API.")
@RestController
public class HealthController {

    private final AppAdminBean adminBean;

    public HealthController(AppAdminBean adminBean) {
        this.adminBean = adminBean;
    }

    @Operation(description = "Gets basic status of the health of the application.")
    @ApiResponse(responseCode = "200", description = "Basic application health data.")
    @GetMapping(value = "health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthInfo health() {
        return adminBean.getHealthInfo();
    }
}
