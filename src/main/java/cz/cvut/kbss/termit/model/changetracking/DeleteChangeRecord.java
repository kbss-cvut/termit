package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

/**
 * Represents a record of asset deletion.
 * @param <T> The label type, {@link String} or {@link MultilingualString}
 */
//@OWLClass(iri = Vocabulary.s_c_smazani_entity) TODO: ontology for DeleteChangeRecord
public class DeleteChangeRecord<T extends Serializable> extends AbstractChangeRecord {
    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private T label;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    public DeleteChangeRecord(Asset<T> changedEntity, URI vocabulary) {
        super(changedEntity);
        this.label = changedEntity.getLabel();
        this.vocabulary = vocabulary;
    }

    public T getLabel() {
        return label;
    }

    public void setLabel(T label) {
        this.label = label;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeleteChangeRecord<?> that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(label, that.label) && Objects.equals(vocabulary, that.vocabulary);
    }

    @Override
    public String toString() {
        return "DeleteChangeRecord{" +
                super.toString() +
                ", label=" + label +
                (vocabulary != null ? ", vocabulary=" + vocabulary : "") +
                '}';
    }

    @Override
    public int compareTo(@Nonnull AbstractChangeRecord o) {
        if (o instanceof UpdateChangeRecord) {
            return 1;
        }
        if (o instanceof PersistChangeRecord) {
            return 1;
        }
        return super.compareTo(o);
    }
}
