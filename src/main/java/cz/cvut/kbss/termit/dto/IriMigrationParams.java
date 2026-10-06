package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.termit.service.IdentifierResolver;

import java.net.URI;

public record IriMigrationParams(URI preferredNamespaceUri) {
    public IriMigrationParams(URI preferredNamespaceUri) {
        if (preferredNamespaceUri != null) {
            final String terminatedNamespace =
                    IdentifierResolver.ensureNamespaceSeparatorTermination(preferredNamespaceUri.toString());
            this.preferredNamespaceUri = URI.create(terminatedNamespace);
        } else {
            this.preferredNamespaceUri = null;
        }
    }

    public IriMigrationParams() {
        this(null);
    }
}
