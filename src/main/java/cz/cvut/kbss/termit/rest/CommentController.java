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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Comments", description = "Comment management API")
@RestController
@RequestMapping("/comments")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class CommentController extends BaseController {

    static final String DEFAULT_LIMIT = "10";

    private static final Logger LOG = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;

    @Autowired
    public CommentController(IdentifierResolver idResolver, Configuration config, CommentService commentService) {
        super(idResolver, config);
        this.commentService = commentService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the comment with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching comment metadata."),
            @ApiResponse(responseCode = "404", description = CommentControllerDoc.NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(value = "/{localName}", produces = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public Comment getById(
            @Parameter(description = CommentControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = CommentControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String localName,
            @Parameter(description = CommentControllerDoc.ID_NAMESPACE_DESCRIPTION,
                       example = CommentControllerDoc.ID_NAMESPACE_EXAMPLE)
            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        return commentService.findRequired(idResolver.resolveIdentifier(namespace, localName));
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates the comment with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comment successfully updated."),
            @ApiResponse(responseCode = "404", description = CommentControllerDoc.NOT_FOUND_DESCRIPTION)
    })
    @PutMapping(value = "/{localName}", consumes = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@Parameter(description = CommentControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                  example = CommentControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                       @PathVariable String localName,
                       @Parameter(description = CommentControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                  example = CommentControllerDoc.ID_NAMESPACE_EXAMPLE)
                       @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                       @Parameter(description = "The updated comment.")
                       @RequestBody Comment update) {
        verifyRequestAndEntityIdentifier(update, idResolver.resolveIdentifier(namespace, localName));
        commentService.update(update);
        LOG.debug("Comment {} successfully updated.", update);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes the comment with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comment successfully removed."),
            @ApiResponse(responseCode = "404", description = CommentControllerDoc.NOT_FOUND_DESCRIPTION)
    })
    @DeleteMapping("/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Parameter(description = CommentControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                  example = CommentControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                       @PathVariable String localName,
                       @Parameter(description = CommentControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                  example = CommentControllerDoc.ID_NAMESPACE_EXAMPLE)
                       @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final Comment toRemove = getById(localName, namespace);
        commentService.remove(toRemove);
        LOG.debug("Comment {} successfully removed.", toRemove);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Adds the specified reaction to the comment with the specified identifier as the current user.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reaction successfully added."),
            @ApiResponse(responseCode = "404", description = CommentControllerDoc.NOT_FOUND_DESCRIPTION)
    })
    @PostMapping(value = "/{localName}/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addReaction(@Parameter(description = CommentControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                       example = CommentControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                            @PathVariable String localName,
                            @Parameter(description = CommentControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                       example = CommentControllerDoc.ID_NAMESPACE_EXAMPLE)
                            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                            @Parameter(description = "Type of the reaction.")
                            @RequestParam(name = "type") String type) {
        final Comment comment = getById(localName, namespace);
        commentService.addReactionTo(comment, type);
        LOG.trace("User reacted with {} to comment {}.", type, comment);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes the current user's reaction to the comment with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reaction successfully removed."),
            @ApiResponse(responseCode = "404", description = CommentControllerDoc.NOT_FOUND_DESCRIPTION)
    })
    @DeleteMapping(value = "/{localName}/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReactionTo(@Parameter(description = CommentControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                            example = CommentControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                                 @PathVariable String localName,
                                 @Parameter(description = CommentControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                            example = CommentControllerDoc.ID_NAMESPACE_EXAMPLE)
                                 @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final Comment comment = getById(localName, namespace);
        commentService.removeMyReactionTo(comment);
        LOG.trace("Reaction on comment {} removed.", comment);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets most recently added/edited comments of the current user.")
    @ApiResponse(responseCode = "200", description = "List of comments.")
    @GetMapping(value = "/last-edited-by-me", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getLastEditedByMe(
            @Parameter(description = "Maximum number of comments to retrieve.")
            @RequestParam(name = "limit", required = false, defaultValue = DEFAULT_LIMIT) int limit) {
        return commentService.findLastEditedByMe(limit);
    }

    /**
     * A couple of constants for the {@link CommentController} API documentation.
     */
    private static final class CommentControllerDoc {
        private static final String ID_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace) unique part of the comment identifier.";
        private static final String ID_LOCAL_NAME_EXAMPLE = "instance-12345";
        private static final String ID_NAMESPACE_DESCRIPTION = "Comment identifier namespace.";
        private static final String ID_NAMESPACE_EXAMPLE = "http://rdfs.org/sioc/types#Comment/";
        private static final String NOT_FOUND_DESCRIPTION = "Comment not found.";
    }
}
