package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.UserService;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.mail.Message;
import cz.cvut.kbss.termit.service.mail.MessageComposer;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CommentChangeNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(CommentChangeNotifier.class);

    static final String COMMENT_CHANGES_TEMPLATE = "comment-news.vm";

    private final CommentService commentService;

    private final TermService termService;

    private final UserService userService;

    private final ChangeRecordService changeRecordService;

    private final MessageAssetFactory messageAssetFactory;

    private final MessageComposer messageComposer;

    public CommentChangeNotifier(CommentService commentService, TermService termService, UserService userService,
                                 ChangeRecordService changeRecordService,
                                 MessageAssetFactory messageAssetFactory, MessageComposer messageComposer) {
        this.commentService = commentService;
        this.termService = termService;
        this.userService = userService;
        this.changeRecordService = changeRecordService;
        this.messageAssetFactory = messageAssetFactory;
        this.messageComposer = messageComposer;
    }

    /**
     * Finds changes to comments in the specified time interval and creates a notification message that can be sent to
     * selected recipients.
     *
     * @param from Interval start
     * @param to   Interval end
     * @return Notification message ready for sending via email
     */
    public Message createCommentChangesMessage(Instant from, Instant to) {
        final Map<Asset<?>, List<Comment>> comments = findChangedComments(from, to);
        final Map<String, Object> variables = new HashMap<>();
        final List<AssetWithComments> assetsWithComments = comments.entrySet().stream()
                                                                   .map(e -> new AssetWithComments(
                                                                           messageAssetFactory.create(e.getKey()),
                                                                           e.getValue().stream()
                                                                            .map(CommentForMessage::new)
                                                                            .collect(Collectors.toList())))
                                                                   .sorted(Comparator.comparing(
                                                                           AssetWithComments::getAsset))
                                                                   .collect(Collectors.toList());
        variables.put("from", LocalDate.ofInstant(from, ZoneId.systemDefault()));
        variables.put("to", LocalDate.ofInstant(to, ZoneId.systemDefault()));
        variables.put("commentedAssets", assetsWithComments);
        return Message.to(resolveNotificationRecipients(comments).stream().map(
                              AbstractUser::getUsername).toArray(String[]::new))
                      .content(messageComposer.composeMessage(COMMENT_CHANGES_TEMPLATE, variables))
                      .subject("TermIt News").build();
    }

    /**
     * Finds comments created or edited in the specified interval, mapped by the assets the comments belong to.
     *
     * @param from Interval start
     * @param to   Interval end
     * @return Map of assets to comments created or edited in the specified interval
     */
    Map<Asset<?>, List<Comment>> findChangedComments(Instant from, Instant to) {
        LOG.debug("Resolving changed comments in time interval <{}, {}>", from, to);
        final List<Comment> comments = commentService.findAll(null, from, to);
        final Map<URI, List<Comment>> reducer = mapCommentsByAsset(comments);
        final Map<Asset<?>, List<Comment>> result = new HashMap<>();
        reducer.forEach((assetUri, lst) -> loadAsset(assetUri).ifPresent(a -> result.put(a, lst)));
        LOG.trace("Found comment changes (comments added or updated) for {} assets.", result.size());
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

    /**
     * Resolves recipients of the notification of changes in comments.
     * <p>
     * The current logic uses for recipients application administrators and authors of vocabularies whose terms are
     * associated with the specified comments.
     *
     * @param commentChanges Changes in comments the recipients should be notified of
     * @return Relevant recipients
     */
    List<User> resolveNotificationRecipients(Map<Asset<?>, List<Comment>> commentChanges) {
        LOG.debug("Gathering notification recipients.");
        final Set<UserAccount> admins = userService.findAll().stream().filter(UserAccount::isAdmin)
                                                   .collect(Collectors.toSet());
        admins.addAll(resolveTermVocabularyAuthors(commentChanges));
        LOG.trace(
                "Found the following potential recipients: {}. Filtering out inactive users and invalid email addresses.",
                admins.stream().map(AbstractUser::getUsername).collect(Collectors.toList()));
        return admins.stream().filter(u -> u.isEnabled() && Utils.isValidEmail(u.getUsername()))
                     .map(UserAccount::toUser).collect(Collectors.toList());
    }

    private Set<UserAccount> resolveTermVocabularyAuthors(Map<Asset<?>, List<Comment>> commentChanges) {
        final Set<URI> vocabularyUris = commentChanges.keySet().stream().filter(a -> a instanceof Term)
                                                      .map(a -> ((Term) a).getVocabulary()).collect(Collectors.toSet());
        return vocabularyUris.stream().map(vUri -> changeRecordService.getAuthors(new Vocabulary(vUri)))
                             .flatMap(Collection::stream).map(User::toUserAccount).collect(Collectors.toSet());
    }

    /**
     * Groups asset with comments concerning it.
     *
     * Allows using simple for loops in Velocity message templates.
     */
    public static class AssetWithComments {
        private final MessageAssetFactory.MessageAsset asset;
        private final List<CommentForMessage> comments;

        public AssetWithComments(MessageAssetFactory.MessageAsset asset, List<CommentForMessage> comments) {
            this.asset = asset;
            this.comments = comments;
        }

        public MessageAssetFactory.MessageAsset getAsset() {
            return asset;
        }

        public List<CommentForMessage> getComments() {
            return comments;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AssetWithComments that = (AssetWithComments) o;
            return asset.equals(that.asset) && comments.equals(that.comments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(asset, comments);
        }
    }

    /**
     * Representation of a comment for mapping to a Velocity message template.
     */
    public static class CommentForMessage {
        private final OperationType operation;
        private final String author;
        private final Instant lastModified;
        private final String content;

        public enum OperationType {
            CREATE, UPDATE
        }

        CommentForMessage(Comment comment) {
            this.operation = comment.getModified() != null ? OperationType.UPDATE : OperationType.CREATE;
            this.author = comment.getAuthor().getFullName();
            this.lastModified = (comment.getModified() != null ? comment.getModified() :
                                 comment.getCreated()).truncatedTo(ChronoUnit.SECONDS);
            this.content = comment.getContent();
        }

        public OperationType getOperation() {
            return operation;
        }

        public String getAuthor() {
            return author;
        }

        public Instant getLastModified() {
            return lastModified;
        }

        public String getContent() {
            return content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CommentForMessage that = (CommentForMessage) o;
            return operation == that.operation && author.equals(that.author) && lastModified.equals(
                    that.lastModified) && content.equals(that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operation, author, lastModified, content);
        }
    }
}
