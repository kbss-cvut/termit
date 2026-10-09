package cz.cvut.kbss.termit.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationType;
import org.springframework.context.ApplicationEvent;

/** Indicates that an identifier of a resource was migrated to a different one. */
@JsonIgnoreProperties("source")
public class IriMigratedEvent extends ApplicationEvent {
    /** The type of the entity whose identifier was migrated */
    private final IriMigrationType migrationType;
    /** The original and new identifiers */
    private final IriMigrationPair iris;

    public IriMigratedEvent(Object source, IriMigrationType migrationType, IriMigrationPair iris) {
        super(source);
        this.migrationType = migrationType;
        this.iris = iris;
    }

    public IriMigrationType getMigrationType() {
        return migrationType;
    }

    public IriMigrationPair getIris() {
        return iris;
    }
}
