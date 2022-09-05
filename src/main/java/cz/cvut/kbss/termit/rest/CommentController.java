package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
public class CommentController extends BaseController {

    static final String DEFAULT_LIMIT = "10";

    private static final Logger LOG = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;

    @Autowired
    public CommentController(IdentifierResolver idResolver, Configuration config, CommentService commentService) {
        super(idResolver, config);
        this.commentService = commentService;
    }

    @GetMapping(value = "/{idFragment}", produces = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public Comment getById(@PathVariable String idFragment,
                           @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        return commentService.findRequired(idResolver.resolveIdentifier(namespace, idFragment));
    }

    @PutMapping(value = "/{idFragment}", consumes = {JsonLd.MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String idFragment,
                       @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                       @RequestBody Comment update) {
        verifyRequestAndEntityIdentifier(update, idResolver.resolveIdentifier(namespace, idFragment));
        commentService.update(update);
        LOG.debug("Comment {} successfully updated.", update);
    }

    @DeleteMapping("/{idFragment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String idFragment,
                       @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final Comment toRemove = getById(idFragment, namespace);
        commentService.remove(toRemove);
        LOG.debug("Comment {} successfully removed.", toRemove);
    }

    @PostMapping(value = "/{idFragment}/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addReaction(@PathVariable String idFragment,
                            @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace,
                            @RequestParam(name = "type") String type) {
        final Comment comment = getById(idFragment, namespace);
        commentService.addReactionTo(comment, type);
        LOG.trace("User reacted with {} to comment {}.", type, comment);
    }

    @DeleteMapping(value = "/{idFragment}/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReactionTo(@PathVariable String idFragment,
                                 @RequestParam(name = Constants.QueryParams.NAMESPACE) String namespace) {
        final Comment comment = getById(idFragment, namespace);
        commentService.removeMyReactionTo(comment);
        LOG.trace("Reaction on comment {} removed.", comment);
    }

    @GetMapping(value = "/last-edited-by-me", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<Comment> getLastEditedByMe(
        @RequestParam(name = "limit", required = false, defaultValue = DEFAULT_LIMIT) int limit) {
        return commentService.findLastEditedByMe(limit);
    }
}
