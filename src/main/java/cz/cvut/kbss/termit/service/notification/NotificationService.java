package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.service.mail.Message;
import cz.cvut.kbss.termit.service.mail.Postman;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class NotificationService {

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
    @Scheduled(cron = "${termit.schedule.cron.notification.comments}")
    public void notifyOfCommentChanges() {
        final Instant now = Utils.timestamp();
        final Instant previous = resolvePreviousRun(now, scheduleConfig.getCron().getNotification().getComments());
        final Message changeNotificationMessage = commentChangeNotifier.createCommentChangesMessage(previous, now);
        postman.sendMessage(changeNotificationMessage);
    }

    private static Instant resolvePreviousRun(Instant now, String cronExpression) {
        final CronExpression expr = CronExpression.parse(cronExpression);
        final Instant next = expr.next(now);
        final Duration interval = Duration.between(now, next);
        return now.minus(interval);
    }
}
