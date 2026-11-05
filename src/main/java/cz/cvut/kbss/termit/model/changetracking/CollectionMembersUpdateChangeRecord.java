package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.RDFCollection;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.List;

/** Special case of {@link UpdateChangeRecord} representing the original and new values as ordered lists. */
@OWLClass(iri = Vocabulary.s_c_update_of_ordered_collection_members)
public class CollectionMembersUpdateChangeRecord extends AttributeChangeRecord<List<URI>> {

    @RDFCollection
    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_original_value)
    private List<URI> originalValue;

    @RDFCollection
    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_new_value)
    private List<URI> newValue;

    public CollectionMembersUpdateChangeRecord() {}

    public CollectionMembersUpdateChangeRecord(Asset<?> changedEntity) {
        super(changedEntity);
    }

    @Override
    public List<URI> getOriginalValue() {
        return originalValue;
    }

    @Override
    public void setOriginalValue(List<URI> originalValue) {
        this.originalValue = originalValue;
    }

    @Override
    public List<URI> getNewValue() {
        return newValue;
    }

    @Override
    public void setNewValue(List<URI> newValue) {
        this.newValue = newValue;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CollectionMembersUpdateChangeRecord)) return false;
        return super.equals(o);
    }
}
