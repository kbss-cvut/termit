package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationType;
import org.springframework.context.ApplicationEvent;

/** Indicates that an identifier of a resource was migrated to a different one. */
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

    public Payload toPayload() {
        return new Payload(migrationType, iris);
    }

    public record Payload(IriMigrationType migrationType, IriMigrationPair iris) {}
}
