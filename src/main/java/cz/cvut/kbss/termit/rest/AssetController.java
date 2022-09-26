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
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.AssetService;
import cz.cvut.kbss.termit.util.Constants;
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

    @GetMapping(value = "/last-edited", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyModifiedAsset> getLastEdited(
            @RequestParam(name = "limit", required = false, defaultValue = DEFAULT_PAGE_SIZE) int limit,
            @RequestParam(name = "forCurrentUserOnly", required = false,
                          defaultValue = "false") Boolean forCurrentUserOnly) {
        return forCurrentUserOnly ? assetService.findMyLastEdited(limit) : assetService.findLastEdited(limit);
    }

    @GetMapping(value = "/last-commented", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyCommentedAsset> getLastCommented(
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false,
                          defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
            @RequestParam(name = Constants.QueryParams.PAGE, required = false,
                          defaultValue = DEFAULT_PAGE) Integer pageNo) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        return assetService.findLastCommented(pageReq).getContent();
    }

    @GetMapping(value = "/last-commented-in-reaction-to-mine",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyCommentedAsset> getLastReactingCommentsToMine(
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false,
                          defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
            @RequestParam(name = Constants.QueryParams.PAGE, required = false,
                          defaultValue = DEFAULT_PAGE) Integer pageNo) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        return assetService.findLastCommentedInReactionToMine(pageReq).getContent();
    }

    @GetMapping(value = "/my-last-commented", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<RecentlyCommentedAsset> getMyLastCommented(
            @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false,
                          defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
            @RequestParam(name = Constants.QueryParams.PAGE, required = false,
                          defaultValue = DEFAULT_PAGE) Integer pageNo) {
        final Pageable pageReq = createPageRequest(pageSize, pageNo);
        return assetService.findMyLastCommented(pageReq).getContent();
    }
}
