package cz.cvut.kbss.termit.model.assignment;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "javaClass")
@OWLClass(iri = Vocabulary.s_c_vyskyt_termu)
public abstract class TermOccurrence extends AbstractEntity implements HasTypes {

    /**
     * Suffix used to identify term occurrence contexts (named graphs) in the repository.
     */
    public static final String CONTEXT_SUFFIX = "occurrences";

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_je_prirazenim_termu)
    private URI term;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_cil, cascade = {CascadeType.MERGE}, fetch = FetchType.EAGER)
    OccurrenceTarget target;

    @OWLDataProperty(iri = DC.Terms.DESCRIPTION)
    private String description;

    @Types
    private Set<String> types;

    private transient Double score;

    public TermOccurrence() {
    }

    public TermOccurrence(URI term, OccurrenceTarget target) {
        this.term = Objects.requireNonNull(term);
        this.target = Objects.requireNonNull(target);
    }

    public URI getTerm() {
        return term;
    }

    public void setTerm(URI term) {
        this.term = term;
    }

    public OccurrenceTarget getTarget() {
        return target;
    }

    public void setTarget(OccurrenceTarget target) {
        this.target = target;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    /**
     * Represents the score of the corresponding text match.
     * <p>
     * Relevant only in case of occurrences resolved by the annotation service.
     *
     * @return Match score, possibly {@code null} (if score was not determined or the occurrence was created manually)
     */
    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "TermOccurrence{<" +
                getUri() +
                ">, term=<" + term +
                ">, target=" + target + '}';
    }

    /**
     * Resolves identifier of the repository context in which this occurrence should be stored.
     * <p>
     * The context is based on the target source's identifier and {@link #CONTEXT_SUFFIX}.
     *
     * @return Repository context URI
     * @see #resolveContext(URI)
     */
    public URI resolveContext() {
        return resolveContext(target.getSource());
    }

    /**
     * Resolves identifier of the repository context in which term occurrences targeting the specified source should be stored.
     * <p>
     * The context is based on the specified source and {@link #CONTEXT_SUFFIX}.
     *
     * @return Repository context URI
     */
    public static URI resolveContext(URI source) {
        Objects.requireNonNull(source);
        String strSource = source.toString();
        if (!strSource.endsWith("/")) {
            strSource += "/";
        }
        return URI.create(strSource + CONTEXT_SUFFIX);
    }
}
