package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.resource.Document;
import java.util.Objects;
import org.springframework.context.ApplicationEvent;

/**
 * Indicates that a {@link Document} asset has changed its label.
 */
public class DocumentRenameEvent extends ApplicationEvent {

    private final String originalName;

    private final String newName;

    public DocumentRenameEvent(Document source, String originalName, String newName) {
        super(source);
        this.originalName = Objects.requireNonNull(originalName);
        this.newName = Objects.requireNonNull(newName);
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getNewName() {
        return newName;
    }

    @Override
    public Document getSource() {
        return (Document) super.getSource();
    }
}
