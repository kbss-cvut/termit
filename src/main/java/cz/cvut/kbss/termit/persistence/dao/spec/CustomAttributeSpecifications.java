package cz.cvut.kbss.termit.persistence.dao.spec;

import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.CustomAttribute_;

import java.net.URI;

public class CustomAttributeSpecifications {

    public static Specification<CustomAttribute> hasDomain(URI domain) {
        return (root, query, cb) -> cb.equal(root.getAttr(CustomAttribute_.domain), domain);
    }

    public static Specification<CustomAttribute> hasRange(URI range) {
        return (root, query, cb) -> cb.equal(root.getAttr(CustomAttribute_.range), range);
    }
}
