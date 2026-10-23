package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.CustomAttribute;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/** Notifies that a {@link CustomAttribute} was marked as symmetric. */
public class CustomAttributeMadeSymmetricEvent extends ApplicationEvent {

    private final CustomAttribute att;

    public CustomAttributeMadeSymmetricEvent(Object source, CustomAttribute att) {
        super(source);
        this.att = Objects.requireNonNull(att);
    }

    public CustomAttribute getAtt() {
        return att;
    }
}
