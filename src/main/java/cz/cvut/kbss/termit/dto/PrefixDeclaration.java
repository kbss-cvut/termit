package cz.cvut.kbss.termit.dto;

import java.util.Objects;

public class PrefixDeclaration {

    /**
     * Prefix and local name separator.
     */
    public static final String SEPARATOR = ":";

    private final String prefix;

    private final String namespace;

    public PrefixDeclaration() {
        this(null, null);
    }

    public PrefixDeclaration(String prefix, String namespace) {
        this.prefix = prefix;
        this.namespace = namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PrefixDeclaration that = (PrefixDeclaration) o;
        return Objects.equals(prefix, that.prefix) && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, namespace);
    }
}
