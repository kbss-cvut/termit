package cz.cvut.kbss.termit.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationType;
import org.springframework.context.ApplicationEvent;

import java.net.URI;

/** Indicates that an identifier of a resource was migrated to a different one. */
@JsonIgnoreProperties("source")
public class IriMigratedEvent extends ApplicationEvent {
    /** The type of the entity whose identifier was migrated */
    private final IriMigrationType migrationType;
    /** The original and new identifiers */
    private final IriMigrationPair iris;
    /** Optional IRI of the affected vocabulary. */
    private final URI vocabularyIri;

    public IriMigratedEvent(Object source, IriMigrationType migrationType, IriMigrationPair iris, URI vocabularyIri) {
        super(source);
        this.migrationType = migrationType;
        this.iris = iris;
        this.vocabularyIri = vocabularyIri;
    }

    public IriMigrationType getMigrationType() {
        return migrationType;
    }

    public IriMigrationPair getIris() {
        return iris;
    }

    public URI getVocabularyIri() {
        return vocabularyIri;
    }
}
