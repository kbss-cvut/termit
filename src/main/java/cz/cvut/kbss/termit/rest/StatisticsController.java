package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.statistics.CountableAssetType;
import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.dto.statistics.TermTypeDistributionDto;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the number of assets of the given type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Number of assets."),
            @ApiResponse(responseCode = "400", description = "Unsupported type of asset.")
    })
    @GetMapping(value = "/count", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public int getCount(@RequestParam(name = "assetType") CountableAssetType assetType) {
        return service.getAssetCount(assetType);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets statistics of the distribution of term types in vocabularies.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Term type distribution statistics.")
    })
    @GetMapping(value = "/term-type-distribution", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermTypeDistributionDto> getTermTypeDistribution() {
        return service.getTermTypeDistribution();
    }
}
