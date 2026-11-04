package cz.cvut.kbss.termit.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.context.ApplicationEvent;

/** Indicates a failure of identifier migration */
@JsonIgnoreProperties("source")
public class IriMigrationFailedEvent extends ApplicationEvent {
    private final String message;
    private final String messageId;

    public IriMigrationFailedEvent(Object source, String message, String messageId) {
        super(source);
        this.message = message;
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessage() {
        return message;
    }
}
