/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest.readonly;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.rest.BaseController;
import cz.cvut.kbss.termit.rest.VocabularyController;
import cz.cvut.kbss.termit.rest.doc.ApiDocConstants;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.readonly.ReadOnlyVocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.security.SecurityConstants.PUBLIC_API_PATH;

@Profile("!no-public-api")
@Tag(name = "Vocabularies - public", description = "No-authorization vocabulary API")
@RestController
@PreAuthorize("permitAll()")
@RequestMapping(PUBLIC_API_PATH + "/vocabularies")
public class ReadOnlyVocabularyController extends BaseController {

    private final ReadOnlyVocabularyService vocabularyService;

    public ReadOnlyVocabularyController(IdentifierResolver idResolver, Configuration config,
                                        ReadOnlyVocabularyService vocabularyService) {
        super(idResolver, config);
        this.vocabularyService = vocabularyService;
    }

    @Operation(description = "Gets all vocabularies managed by the system.")
    @ApiResponse(responseCode = "200", description = "List of vocabularies ordered by label.")
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<ReadOnlyVocabulary> getAll() {
        return vocabularyService.findAll();
    }

    @Operation(description = "Gets detail of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching vocabulary metadata."),
            @ApiResponse(responseCode = "404", description = VocabularyController.ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ReadOnlyVocabulary getById(
            @Parameter(description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        return vocabularyService.findRequired(
                resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), localName));
    }

    /**
     * Gets imports (including transitive) of vocabulary with the specified identification
     */
    @Operation(
            description = "Gets identifiers of vocabularies imported (including transitive imports) by the vocabulary with the specified identification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection of vocabulary identifiers."),
            @ApiResponse(responseCode = "404", description = VocabularyController.ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/imports", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getTransitiveImports(
            @Parameter(description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final ReadOnlyVocabulary vocabulary = getById(localName, namespace);
        return vocabularyService.getTransitivelyImportedVocabularies(vocabulary);
    }

    @Operation(description = "Gets a list of snapshots of the vocabulary with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                         description = "A list of snapshots or a snapshot valid at the requested datetime."),
            @ApiResponse(responseCode = "400", description = "Provided timestamp is invalid."),
            @ApiResponse(responseCode = "404", description = VocabularyController.ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}/versions", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(
            @Parameter(description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                       example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace,
            @Parameter(
                    description = "Timestamp at which the returned valid was valid. ISO-formatted datetime.",
                    example = ApiDocConstants.DATETIME_EXAMPLE)
            @RequestParam(name = "at", required = false) Optional<String> at) {
        final ReadOnlyVocabulary vocabulary = getById(localName, namespace);
        if (at.isPresent()) {
            final Instant instant = RestUtils.parseTimestamp(at.get());
            return ResponseEntity.ok(vocabularyService.findVersionValidAt(vocabulary, instant));
        }
        return ResponseEntity.ok(vocabularyService.findSnapshots(vocabulary));
    }
}
