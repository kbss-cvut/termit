package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

@OWLClass(iri = Vocabulary.s_c_vyskyt_termu)
public abstract class TermOccurrence extends TermAssignment {

    /**
     * Suffix used to identify term occurrence contexts (named graphs) in the repository.
     */
    public static final String CONTEXT_SUFFIX = "occurrences";

    private transient Double score;

    public TermOccurrence() {
    }

    public TermOccurrence(URI term, OccurrenceTarget target) {
        super(term, target);
    }

    @Override
    public OccurrenceTarget getTarget() {
        assert target == null || target instanceof OccurrenceTarget;
        return (OccurrenceTarget) target;
    }

    public void setTarget(OccurrenceTarget target) {
        this.target = target;
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
        return "TermOccurrence - " + super.toString();
    }
}
