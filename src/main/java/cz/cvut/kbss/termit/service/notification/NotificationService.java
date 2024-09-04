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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final Configuration.Schedule scheduleConfig;

    private final CommentChangeNotifier commentChangeNotifier;

    private final Postman postman;

    public NotificationService(Configuration config, CommentChangeNotifier commentChangeNotifier, Postman postman) {
        this.scheduleConfig = config.getSchedule();
        this.commentChangeNotifier = commentChangeNotifier;
        this.postman = postman;
    }

    /**
     * Notifies selected users of comments created since last notification.
     * <p>
     * Scheduling is done via configured CRON expression.
     */
    @Scheduled(cron = "${termit.schedule.cron.notification.comments:-}")
    public void notifyOfCommentChanges() {
        LOG.debug("Running comment change notification.");
        final Instant now = Utils.timestamp();
        final Instant previous = resolvePreviousRun(now, scheduleConfig.getCron().getNotification().getComments());
        final Optional<Message> changeNotificationMessage = commentChangeNotifier.createCommentChangesMessage(previous, now);
        changeNotificationMessage.ifPresent(postman::sendMessage);
    }

    private static Instant resolvePreviousRun(Instant now, String cronExpression) {
        final CronExpression expr = CronExpression.parse(cronExpression);
        final OffsetDateTime nextDatetime = expr.next(OffsetDateTime.ofInstant(now, ZoneId.systemDefault()));
        if (nextDatetime == null) {
            throw new IllegalArgumentException(
                    "Invalid scheduling CRON expression " + expr + ". Unable to determine next run.");
        }
        final Instant next = nextDatetime.toInstant();
        final Duration interval = Duration.between(now, next);
        return now.minus(interval);
    }
}
