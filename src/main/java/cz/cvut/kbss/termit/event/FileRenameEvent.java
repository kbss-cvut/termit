package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.resource.File;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Indicates that a {@link cz.cvut.kbss.termit.model.resource.File} asset has changed its label.
 */
public class FileRenameEvent extends ApplicationEvent {

    private final String originalName;

    private final String newName;

    public FileRenameEvent(File source, String originalName, String newName) {
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
    public File getSource() {
        return (File) super.getSource();
    }
}
