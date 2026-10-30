package cz.cvut.kbss.termit.persistence.dao.spec;

import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.CustomAttribute_;
import org.eclipse.rdf4j.model.vocabulary.OWL;

import java.net.URI;
import java.util.Set;

/** Contains factory methods for creating {@link Specification}s for retrieving {@link CustomAttribute} instances. */
public class CustomAttributeSpecifications {

    public static Specification<CustomAttribute> hasDomain(URI domain) {
        return (root, _, cb) -> cb.equal(root.getAttr(CustomAttribute_.domain), domain);
    }

    public static Specification<CustomAttribute> hasRange(URI range) {
        return (root, _, cb) -> cb.equal(root.getAttr(CustomAttribute_.range), range);
    }

    public static Specification<CustomAttribute> isSymmetric() {
        // TODO Use JOPA OWL constant when available in the next release
        // TODO Next JOPA release should allow referencing Types specification from the static metamodel
        return (root, _, cb) -> cb.isMember(URI.create(OWL.SYMMETRICPROPERTY.stringValue()), root.getAttr("types"));
    }

    public static Specification<CustomAttribute> allWithIdentifiers(Set<URI> identifiers) {
        return (root, query, cb) -> root.in(identifiers);
    }
}
