package cz.cvut.kbss.termit.persistence.dao.spec;

import cz.cvut.kbss.jopa.vocabulary.OWL;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.CustomAttribute_;

import java.net.URI;

/** Contains factory methods for creating {@link Specification}s for retrieving {@link CustomAttribute} instances. */
public class CustomAttributeSpecifications {

    public static Specification<CustomAttribute> hasDomain(URI domain) {
        return (root, _, cb) -> cb.equal(root.getAttr(CustomAttribute_.domain), domain);
    }

    public static Specification<CustomAttribute> hasRange(URI range) {
        return (root, _, cb) -> cb.equal(root.getAttr(CustomAttribute_.range), range);
    }

    public static Specification<CustomAttribute> isSymmetric() {
        return (root, _, cb) -> cb.isMember(URI.create(OWL.SYMMETRIC_PROPERTY), root.getAttr(CustomAttribute_.types));
    }
}
