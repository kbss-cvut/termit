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

import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.rest.dto.AssetType;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Identifiers", description = "Identifier generation API")
@RestController
@RequestMapping("/identifiers")
public class IdentifierController extends BaseController {

    @Autowired
    public IdentifierController(IdentifierResolver identifierResolver, Configuration config) {
        super(identifierResolver, config);
    }

    /**
     * Returns identifier which would be generated by the application for the specified asset name (using the configured
     * namespace).
     *
     * @param name       name of the asset
     * @param contextIri IRI of the context (vocabularyIri for terms, documentIri for files, null otherwise)
     * @return Generated vocabulary identifier
     */
    @Operation(
            description = "Returns identifier which would be generated by the application for the specified asset name (using the configured namespace).")
    @ApiResponse(responseCode = "200", description = "Generated identifier.")
    @PreAuthorize("permitAll()")
    @PostMapping
    public URI generateIdentifier(@Parameter(description = "Name of the asset for which to generate identifier.")
                                  @RequestParam("name") String name,
                                  @Parameter(
                                          description = "IRI to use as context for identifier generation. For example, for a term it would the vocabulary IRI.")
                                  @RequestParam(value = "contextIri", required = false) String contextIri,
                                  @Parameter(description = "Type of the asset for which to generate the identifier.")
                                  @RequestParam("assetType") AssetType assetType) {
        final Configuration.Namespace cfgNamespace = config.getNamespace();

        switch (assetType) {
            case TERM:
                ensureContextIriIsNotNull(assetType, contextIri);
                return idResolver
                        .generateDerivedIdentifier(URI.create(contextIri), cfgNamespace.getTerm().getSeparator(), name);
            case VOCABULARY:
                ensureContextIriIsNull(assetType, contextIri);
                return idResolver.generateIdentifier(cfgNamespace.getVocabulary(), name);
            case FILE:
                ensureContextIriIsNotNull(assetType, contextIri);
                return idResolver
                        .generateDerivedIdentifier(URI.create(contextIri), cfgNamespace.getFile().getSeparator(), name);
            case RESOURCE:
                ensureContextIriIsNull(assetType, contextIri);
                return idResolver.generateIdentifier(cfgNamespace.getResource(), name);
            default:
                throw new UnsupportedOperationException("Unsupported asset type " + assetType + " supplied.");
        }
    }

    private static void ensureContextIriIsNull(AssetType assetType, String contextIri) {
        if (contextIri != null) {
            throw new InvalidParameterException("ContextIri must NOT be supplied for asset of type " + assetType);
        }
    }

    private static void ensureContextIriIsNotNull(AssetType assetType, String contextIri) {
        if (contextIri == null) {
            throw new InvalidParameterException("ContextIri must be supplied for asset of type " + assetType);
        }
    }
}
