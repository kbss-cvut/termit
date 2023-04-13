package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.rest.dto.HealthInfo;
import cz.cvut.kbss.termit.service.jmx.AppAdminBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final AppAdminBean adminBean;

    public HealthController(AppAdminBean adminBean) {
        this.adminBean = adminBean;
    }

    @GetMapping(value = "health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthInfo health() {
        return adminBean.getHealthInfo();
    }
}
