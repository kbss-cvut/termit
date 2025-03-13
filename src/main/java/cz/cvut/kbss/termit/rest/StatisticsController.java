package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Statistics", description = "Statistics API")
@RestController
@RequestMapping("/statistics")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class StatisticsController {

    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets statistics of the term distribution in vocabularies.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Term distribution statistics.")
    })
    @GetMapping(value = "/term-distribution", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<DistributionDto> getTermDistribution() {
        return service.getTermDistribution();
    }
}
