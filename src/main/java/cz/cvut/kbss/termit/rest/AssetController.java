/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.rest.doc.ApiDocConstants;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.AssetService;
import cz.cvut.kbss.termit.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cz.cvut.kbss.termit.rest.util.RestUtils.createPageRequest;


@Tag(name = "Assets", description = "API with basic info about all assets")
@RestController
@RequestMapping("/assets")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class AssetController {

    static final String DEFAULT_PAGE_SIZE = "10";
    static final String DEFAULT_PAGE = "0";

    private final AssetService assetService;

    @Autowired
    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets recently edited assets with info on the change.")
    @ApiResponse(responseCode = "200", description = "List of changed assets.")
    @GetMapping(value = "/last-edited", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyModifiedAsset> getLastEdited(@Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
                                                     @RequestParam(name = Constants.QueryParams.PAGE_SIZE,
                                                                   required = false,
                                                                   defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
                                                     @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
                                                     @RequestParam(name = Constants.QueryParams.PAGE, required = false,
                                                                   defaultValue = DEFAULT_PAGE) Integer pageNo,
                                                     @Parameter(
                                                             description = "Whether only changes done by the current user should be retrieved.")
                                                     @RequestParam(name = "forCurrentUserOnly", required = false,
                                                                   defaultValue = "false") Boolean forCurrentUserOnly) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        return forCurrentUserOnly ? assetService.findMyLastEdited(pageReq).getContent() :
               assetService.findLastEdited(pageReq).getContent();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets recently commented assets with the relevant comments.")
    @ApiResponse(responseCode = "200", description = "List of recent comments.")
    @GetMapping(value = "/last-commented", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyCommentedAsset> getLastCommented(
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE,
                          required = false,
                          defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE,
                          required = false,
                          defaultValue = DEFAULT_PAGE) Integer pageNo) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        return assetService.findLastCommented(pageReq).getContent();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets recently commented assets in reaction to the current user's comment, with the relevant comments.")
    @ApiResponse(responseCode = "200", description = "List of matching comments.")
    @GetMapping(value = "/last-commented-in-reaction-to-mine",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyCommentedAsset> getLastReactingCommentsToMine(
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false,
                          defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE, required = false,
                          defaultValue = DEFAULT_PAGE) Integer pageNo) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        return assetService.findLastCommentedInReactionToMine(pageReq).getContent();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets recently commented assets whose author is the current user, with the relevant comments.")
    @ApiResponse(responseCode = "200", description = "List of matching comments.")
    @GetMapping(value = "/my-last-commented", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyCommentedAsset> getMyLastCommented(
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false,
                          defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
            @RequestParam(name = Constants.QueryParams.PAGE, required = false,
                          defaultValue = DEFAULT_PAGE) Integer pageNo) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        return assetService.findMyLastCommented(pageReq).getContent();
    }
}
