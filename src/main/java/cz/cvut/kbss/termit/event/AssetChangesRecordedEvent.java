package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;

public class AssetChangesRecordedEvent<T extends AbstractChangeRecord> extends ApplicationEvent {

    private final Collection<T> changeRecords;

    public AssetChangesRecordedEvent(Object source, Collection<T> changeRecords) {
        super(source);
        this.changeRecords = changeRecords;
    }

    public Collection<T> getChangeRecords() {
        return changeRecords;
    }
}
