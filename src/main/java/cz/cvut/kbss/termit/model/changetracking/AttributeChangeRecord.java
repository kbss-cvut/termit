package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.annotations.MappedSuperclass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Vocabulary;

import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.Objects;

@MappedSuperclass
public abstract class AttributeChangeRecord<T> extends AbstractChangeRecord {
    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_has_changed_attribute)
    private URI changedAttribute;

    public AttributeChangeRecord() {}

    public AttributeChangeRecord(Asset<?> changedEntity) {
        super(changedEntity);
    }

    public abstract T getOriginalValue();

    public abstract void setOriginalValue(T originalValue);

    public abstract T getNewValue();

    public abstract void setNewValue(T newValue);

    public URI getChangedAttribute() {
        return changedAttribute;
    }

    public void setChangedAttribute(URI changedAttribute) {
        this.changedAttribute = changedAttribute;
    }

    @Override
    public String toString() {
        return "AttributeChangeRecord{" + super.toString() + "changedAttribute=" + changedAttribute + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AttributeChangeRecord<?> that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(changedAttribute, that.changedAttribute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), changedAttribute);
    }

    @Override
    public int compareTo(@Nonnull AbstractChangeRecord o) {
        if (o instanceof PersistChangeRecord) {
            return 1;
        }
        if (o instanceof DeleteChangeRecord) {
            return -1;
        }
        return super.compareTo(o);
    }
}
