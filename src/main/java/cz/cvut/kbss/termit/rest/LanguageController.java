/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.util.Configuration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Languages", description = "Taxonomies/languages available in the system")
@RestController
@RequestMapping(LanguageController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class LanguageController extends BaseController {

    public static final String PATH = "/language";

    private final LanguageService service;

    @Autowired
    public LanguageController(IdentifierResolver idResolver, Configuration config, LanguageService service) {
        super(idResolver, config);
        this.service = service;
    }

    @Operation(description = "Gets ontological types that can be used to classify terms.")
    @ApiResponse(responseCode = "200", description = "Ontological types collection.")
    @PreAuthorize("permitAll()")
    @GetMapping(value = "/types", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Term> getTermTypes() {
        return service.getTermTypes();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets access levels that can be used to authorize user actions.")
    @ApiResponse(responseCode = "200", description = "List of available access levels.")
    @GetMapping(value = "/accessLevels", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RdfsResource> getAccessLevels() {
        return service.getAccessLevels();
    }

    @Operation(description = "Gets available term state options.")
    @ApiResponse(responseCode = "200", description = "Term state options.")
    @PreAuthorize("permitAll()")
    @GetMapping(value = "/states", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RdfsResource> getTermStates() {
        return service.getTermStates();
    }
}
