package cz.cvut.kbss.termit.model.occurrence;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.selector.Selector;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "javaClass")
@OWLClass(iri = Vocabulary.s_c_cil_vyskytu)
public abstract class OccurrenceTarget extends AbstractEntity {

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zdroj)
    private URI source;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_selektor, cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<Selector> selectors;

    public OccurrenceTarget() {
    }

    public OccurrenceTarget(Asset<?> source) {
        this.source = Objects.requireNonNull(source).getUri();
    }

    public URI getSource() {
        return source;
    }

    public void setSource(URI source) {
        this.source = source;
    }

    public Set<Selector> getSelectors() {
        return selectors;
    }

    public void setSelectors(Set<Selector> selectors) {
        this.selectors = selectors;
    }

    @Override
    public String toString() {
        return "OccurrenceTarget{<" + getUri() +
                ">, source=<" + source +
                ">, selectors=" + selectors +
                "} " + super.toString();
    }
}
