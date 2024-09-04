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
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
                Optional.of(testMessage));

        sut.notifyOfCommentChanges();
        verify(postman).sendMessage(testMessage);
    }

    @Test
    void notifyOfCommentChangesDoesNotSendAnythingWhenCommentChangeNotifierReturnsEmptyMessage() {
        final String cronExpr = "0 1 5 * * MON";
        configuration.getSchedule().getCron().getNotification().setComments(cronExpr);
        when(commentChangeNotifier.createCommentChangesMessage(any(Instant.class), any(Instant.class))).thenReturn(
                Optional.empty());

        sut.notifyOfCommentChanges();
        verify(postman, never()).sendMessage(any());
    }
}
