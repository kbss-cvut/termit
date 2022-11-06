package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.UserService;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.mail.Message;
import cz.cvut.kbss.termit.service.mail.MessageComposer;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static cz.cvut.kbss.termit.service.notification.CommentChangeNotifier.COMMENT_CHANGES_TEMPLATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentChangeNotifierTest {

    @Mock
    private CommentService commentService;

    @Mock
    private TermService termService;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private UserService userService;

    @Mock
    private MessageComposer messageComposer;

    @Mock
    private MessageAssetFactory messageAssetFactory;

    @InjectMocks
    private CommentChangeNotifier sut;

    @Test
    void resolveCommentsRetrievesCommentsInIntervalAndMapsThemByTerm() {
        final List<Term> terms = List.of(Generator.generateTermWithId(), Generator.generateTermWithId());
        final Map<Term, List<Comment>> expected = new HashMap<>();
        terms.forEach(t -> {
            when(termService.find(t.getUri())).thenReturn(Optional.of(t));
            expected.put(t, Generator.generateComments(t));
        });
        final Instant from = Utils.timestamp().minus(5, ChronoUnit.DAYS);
        final Instant to = Utils.timestamp();
        when(commentService.findAll(null, from, to)).thenReturn(
                expected.values().stream().flatMap(Collection::stream).collect(
                        Collectors.toList()));

        final Map<Asset<?>, List<Comment>> result = sut.findChangedComments(from, to);
        assertEquals(expected, result);
        verify(commentService).findAll(null, from, to);
        terms.forEach(t -> verify(termService).find(t.getUri()));
    }

    @Test
    void resolveRecipientsRetrievesAdminUsers() {
        final List<UserAccount> users = IntStream.range(0, 10).mapToObj(i -> {
            final UserAccount user = Generator.generateUserAccount();
            if (i % 2 == 0) {
                user.addType(Vocabulary.s_c_administrator_termitu);
            }
            return user;
        }).collect(Collectors.toList());
        when(userService.findAll()).thenReturn(users);

        final List<User> result = sut.resolveNotificationRecipients(Collections.emptyMap());
        assertThat(result,
                   containsSameEntities(users.stream().filter(UserAccount::isAdmin).collect(Collectors.toSet())));
        verify(userService).findAll();
    }

    @Test
    void resolveRecipientsRetrievesAuthorsOfVocabulariesAssociatedWithSpecifiedTerms() {
        final Term tOne = Generator.generateTermWithId(Generator.generateUri());
        final Term tTwo = Generator.generateTermWithId(Generator.generateUri());
        final UserAccount admin = Generator.generateUserAccount();
        admin.addType(Vocabulary.s_c_administrator_termitu);
        final User author = Generator.generateUserWithId();
        when(userService.findAll()).thenReturn(Collections.singletonList(admin));
        when(changeRecordService.getAuthors(any(Asset.class))).thenReturn(Collections.singleton(author));

        final List<User> result = sut.resolveNotificationRecipients(
                Map.of(tOne, Collections.singletonList(Generator.generateComment(tOne)),
                       tTwo, Collections.singletonList(Generator.generateComment(tTwo))));
        assertThat(result, hasItems(admin.toUser(), author));
        verify(changeRecordService).getAuthors(new cz.cvut.kbss.termit.model.Vocabulary(tOne.getVocabulary()));
        verify(changeRecordService).getAuthors(new cz.cvut.kbss.termit.model.Vocabulary(tTwo.getVocabulary()));
    }

    @Test
    void commentForMessageUsesCommentCreationDateAsLastModifiedTimestampByDefault() {
        final Comment comment = Generator.generateComment(Generator.generateUser(), Generator.generateTermWithId());
        assertNotNull(comment.getCreated());
        comment.setModified(null);
        final CommentChangeNotifier.CommentForMessage sut = new CommentChangeNotifier.CommentForMessage(comment);
        assertEquals(CommentChangeNotifier.CommentForMessage.OperationType.CREATE, sut.getOperation());
        assertEquals(comment.getCreated().truncatedTo(ChronoUnit.SECONDS), sut.getLastModified());
    }

    @Test
    void commentForMessageUsesCommentModifiedDateAsLastModifiedTimestampWhenPresent() {
        final Comment comment = Generator.generateComment(Generator.generateUser(), Generator.generateTermWithId());
        comment.setCreated(Instant.EPOCH);
        comment.setModified(Instant.now());
        assertNotNull(comment.getCreated());
        final CommentChangeNotifier.CommentForMessage sut = new CommentChangeNotifier.CommentForMessage(comment);
        assertEquals(CommentChangeNotifier.CommentForMessage.OperationType.UPDATE, sut.getOperation());
        assertEquals(comment.getModified().truncatedTo(ChronoUnit.SECONDS), sut.getLastModified());
    }

    @Test
    void createCommentChangesMessageComposesMessageUsingResolvedCommentsToResolvedRecipients() throws Exception {
        final Instant from = Utils.timestamp().minus(7, ChronoUnit.DAYS);
        final Instant to = Utils.timestamp();
        final UserAccount author = Generator.generateUserAccount();
        when(userService.findAll()).thenReturn(Collections.singletonList(author));
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        // Simulate autowired configuration
        final Field configField = Term.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(term, new Configuration());
        final Comment comment = Generator.generateComment(author.toUser(), term);
        when(termService.find(term.getUri())).thenReturn(Optional.of(term));
        when(changeRecordService.getAuthors(any())).thenReturn(Collections.singleton(author.toUser()));
        when(commentService.findAll(any(), any(Instant.class), any(Instant.class))).thenReturn(
                Collections.singletonList(comment));
        final String link = "http://localhost/termit";
        when(messageAssetFactory.create(term)).thenReturn(
                new MessageAssetFactory.MessageAsset(term.getPrimaryLabel(), link));
        when(messageComposer.composeMessage(any(), anyMap())).thenReturn("Test message content");

        final Message result = sut.createCommentChangesMessage(from, to);
        assertEquals(Collections.singletonList(author.getUsername()), result.getRecipients());
        verify(changeRecordService).getAuthors(new cz.cvut.kbss.termit.model.Vocabulary(term.getVocabulary()));
        verify(commentService).findAll(null, from, to);
        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messageComposer).composeMessage(eq(COMMENT_CHANGES_TEMPLATE), captor.capture());
        final Map<String, Object> variables = captor.getValue();
        assertEquals(LocalDate.ofInstant(from, ZoneId.systemDefault()), variables.get("from"));
        assertEquals(LocalDate.ofInstant(to, ZoneId.systemDefault()), variables.get("to"));
        assertEquals(Collections.singletonList(new CommentChangeNotifier.AssetWithComments(
                             new MessageAssetFactory.MessageAsset(term.getPrimaryLabel(), link),
                             Collections.singletonList(new CommentChangeNotifier.CommentForMessage(comment)))),
                     variables.get("commentedAssets"));
    }
}
