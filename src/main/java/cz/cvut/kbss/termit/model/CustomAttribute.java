package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Context;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

// Cannot use @Context(CONTEXT) because of JOPA bug #356
@Context(Vocabulary.s_i_termit + "/custom-attributes")
@OWLClass(iri = Vocabulary.s_c_vlastni_atribut)
public class CustomAttribute extends RdfsResource {

    public static final String CONTEXT = Vocabulary.s_i_termit + "/custom-attributes";

    @OWLAnnotationProperty(iri = RDFS.DOMAIN)
    private URI domain;

    @OWLAnnotationProperty(iri = RDFS.RANGE)
    private URI range;

    public CustomAttribute() {
    }

    public CustomAttribute(URI uri, MultilingualString label,
                           MultilingualString comment) {
        super(uri, label, comment, RDF.PROPERTY);
    }

    public URI getDomain() {
        return domain;
    }

    public void setDomain(URI domain) {
        this.domain = domain;
    }

    public URI getRange() {
        return range;
    }

    public void setRange(URI range) {
        this.range = range;
    }
}
