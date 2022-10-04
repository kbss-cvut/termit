package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.comment.CommentService;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@Component
public class CommentChangeResolver {

    private final CommentService commentService;

    private final TermService termService;

    public CommentChangeResolver(CommentService commentService, TermService termService) {
        this.commentService = commentService;
        this.termService = termService;
    }

    /**
     * Finds comments created or edited in the specified interval, mapped by the assets the comments belong to.
     *
     * @param from Interval start
     * @param to   Interval end
     * @return Map of assets to comments created or edited in the specified interval
     */
    public Map<Asset<?>, List<Comment>> resolveComments(Instant from, Instant to) {
        final List<Comment> comments = commentService.findAll(null, from, to);
        final Map<URI, List<Comment>> reducer = mapCommentsByAsset(comments);
        final Map<Asset<?>, List<Comment>> result = new HashMap<>();
        reducer.forEach((assetUri, lst) -> loadAsset(assetUri).ifPresent(a -> result.put(a, lst)));
        return result;
    }

    private Map<URI, List<Comment>> mapCommentsByAsset(List<Comment> comments) {
        Map<URI, List<Comment>> reducer = new HashMap<>();
        comments.forEach(c -> {
            reducer.computeIfAbsent(c.getAsset(), (k) -> new ArrayList<>());
            reducer.get(c.getAsset()).add(c);
        });
        return reducer;
    }

    private Optional<? extends Asset<?>> loadAsset(URI uri) {
        // Note that this current works only for terms, as other types of assets are not commented.
        // If comments are added to other types of assets, this method will have to be modified to account for that.
        return termService.find(uri);
    }
}
