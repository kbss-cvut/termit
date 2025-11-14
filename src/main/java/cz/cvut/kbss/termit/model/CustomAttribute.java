package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Context;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Set;

@Context(CustomAttribute.CONTEXT)
@OWLClass(iri = Vocabulary.s_c_vlastni_atribut)
public class CustomAttribute extends RdfsResource {

    public static final String CONTEXT = Vocabulary.s_i_termit + "/custom-attributes";

    @OWLAnnotationProperty(iri = Vocabulary.s_p_as_relationship)
    private Set<URI> annotatedRelationships;

    public CustomAttribute() {
    }

    public CustomAttribute(URI uri, MultilingualString label,
                           MultilingualString comment) {
        super(uri, label, comment, RDF.PROPERTY);
    }


    public Set<URI> getAnnotatedRelationships() {
        return annotatedRelationships;
    }

    public void setAnnotatedRelationships(Set<URI> annotatedRelationships) {
        this.annotatedRelationships = annotatedRelationships;
    }
}
