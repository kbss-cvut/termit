package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.jopa.model.IRI;
import cz.cvut.kbss.termit.model.CustomAttribute_;
import cz.cvut.kbss.termit.model.Term_;
import cz.cvut.kbss.termit.model.Vocabulary_;

import java.net.URI;

/**
 * Type of the entity whose IRI is being migrated
 */
public enum IriMigrationType {
    VOCABULARY(Vocabulary_.entityClassIRI),
    TERM(Term_.entityClassIRI),
    CUSTOM_ATTRIBUTE(CustomAttribute_.entityClassIRI);

    private final URI entityType;

    IriMigrationType(IRI entityType) {
        this.entityType = entityType.toURI();
    }

    public URI getEntityType() {
        return entityType;
    }

    /**
     * Resolves {@link IriMigrationType} by the entity type IRI
     *
     * @param entityType the IRI of the entity type
     * @return Resolved {@link IriMigrationType} or {@code null}
     */
    public static IriMigrationType fromEntityType(URI entityType) {
        for (IriMigrationType type : IriMigrationType.values()) {
            if (type.entityType.equals(entityType)) return type;
        }
        return null;
    }
}
