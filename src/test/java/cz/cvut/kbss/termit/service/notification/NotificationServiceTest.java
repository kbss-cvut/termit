package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.service.mail.Message;
import cz.cvut.kbss.termit.service.mail.Postman;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private CommentChangeNotifier commentChangeNotifier;

    @Mock
    private Postman postman;

    @Spy
    private Configuration configuration = new Configuration();

    @InjectMocks
    private NotificationService sut;

    @Test
    void notifyOfCommentChangesCorrectlyResolvesTimeIntervalFromCronExpression() {
        final String cronExpr = "0 1 5 * * MON";
        configuration.getSchedule().getCron().getNotification().setComments(cronExpr);
        final Instant now = Utils.timestamp();
        final Instant nextRun = OffsetDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(5)
                                              .withMinute(1).withSecond(0).toInstant();
        final Instant previous = now.minus(Duration.between(now, nextRun));

        sut.notifyOfCommentChanges();
        final ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        final ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(commentChangeNotifier).createCommentChangesMessage(from.capture(), to.capture());
        assertThat(Math.abs(previous.toEpochMilli() - from.getValue().toEpochMilli()), lessThan(6000L));
        assertThat(Math.abs(now.toEpochMilli() - to.getValue().toEpochMilli()), lessThan(6000L));
    }

    @Test
    void notifyOfCommentChangesSendsMessageProvidedByChangeNotifier() {
        final String cronExpr = "0 1 5 * * MON";
        configuration.getSchedule().getCron().getNotification().setComments(cronExpr);
        final Message testMessage = Message.to("test@example.org").content("Test message").subject("Test").build();
        when(commentChangeNotifier.createCommentChangesMessage(any(Instant.class), any(Instant.class))).thenReturn(
                testMessage);

        sut.notifyOfCommentChanges();
        verify(postman).sendMessage(testMessage);
    }
}
