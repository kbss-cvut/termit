package cz.cvut.kbss.termit.persistence.dao.spec;

import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.CustomAttribute_;

import java.net.URI;
import java.util.Set;

/** Contains factory methods for creating {@link Specification}s for retrieving {@link CustomAttribute} instances. */
public class CustomAttributeSpecifications {

    public static Specification<CustomAttribute> hasDomain(URI domain) {
        return (root, query, cb) -> cb.equal(root.getAttr(CustomAttribute_.domain), domain);
    }

    public static Specification<CustomAttribute> hasRange(URI range) {
        return (root, query, cb) -> cb.equal(root.getAttr(CustomAttribute_.range), range);
    }

    public static Specification<CustomAttribute> allWithIdentifiers(Set<URI> identifiers) {
        return (root, query, cb) -> root.in(identifiers);
    }
}
