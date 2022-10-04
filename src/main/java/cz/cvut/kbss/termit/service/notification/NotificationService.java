package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final Configuration.Schedule scheduleConfig;

    private final CommentChangeResolver commentChangeResolver;

    public NotificationService(Configuration config, CommentChangeResolver commentChangeResolver) {
        this.scheduleConfig = config.getSchedule();
        this.commentChangeResolver = commentChangeResolver;
    }

    /**
     * Notifies selected users of comments created since last notification.
     *
     * Scheduling is done via configured CRON expression.
     */
    @Scheduled(cron = "${termit.schedule.cron.notification.comments}")
    public void notifyOfNewComments() {
        final Instant now = Utils.timestamp();
        final Instant previous = resolvePreviousRun(now, scheduleConfig.getCron().getNotification().getComments());
        final Map<Asset<?>, List<Comment>> comments = commentChangeResolver.resolveComments(previous, now);
        // TODO
    }

    private static Instant resolvePreviousRun(Instant now, String cronExpression) {
        final CronExpression expr = CronExpression.parse(cronExpression);
        final Instant next = expr.next(now);
        final Duration interval = Duration.between(now, next);
        return now.minus(interval);
    }
}
