package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

/**
 * Synthetic parent class for entity classes that can be a subject of a {@link cz.cvut.kbss.termit.model.acl.AccessControlRecord}.
 * <p>
 * This class exists to allow using a generic parameter in {@link cz.cvut.kbss.termit.model.acl.AccessControlRecord}.
 * Since entity attribute values must be entity classes themselves, a mapped superclass would not be enough.
 * <p>
 * However, this class is completely synthetic, it has no bearing on the underlying data or the conceptual model. It is
 * purely a technical means of achieving the ability to have a  generic access control record with specializations for
 * different access control holder types.
 */
@OWLClass(iri = Vocabulary.s_c_Agent)
public abstract class AccessControlAgent implements HasIdentifier {

    @Id
    private URI uri;

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }
}
