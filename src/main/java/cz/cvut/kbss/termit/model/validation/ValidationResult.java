package cz.cvut.kbss.termit.model.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import java.net.URI;
import org.topbraid.shacl.vocabulary.SH;

@OWLClass(iri = SH.BASE_URI + "ValidationResult")
public class ValidationResult {

    /**
     * Identifier of the affected term.
     */
    @Id
    private URI termUri;

    /**
     * Severity of the problem.
     */
    @OWLObjectProperty(iri = SH.BASE_URI + "resultSeverity")
    private URI severity;

    /**
     * Map from language tag to the validation message in the given language.
     */
    @OWLDataProperty(iri = SH.BASE_URI + "resultMessage")
    private MultilingualString message;

    /**
     * Identifier of the cause of the issue.
     */
    @OWLObjectProperty(iri = SH.BASE_URI + "sourceShape")
    private URI issueCauseUri;

    public URI getTermUri() {
        return termUri;
    }

    public ValidationResult setTermUri(URI termUri) {
        this.termUri = termUri;
        return this;
    }

    public URI getSeverity() {
        return severity;
    }

    public ValidationResult setSeverity(URI severity) {
        this.severity = severity;
        return this;
    }

    public MultilingualString getMessage() {
        return message;
    }

    public ValidationResult setMessage(MultilingualString message) {
        this.message = message;
        return this;
    }

    public URI getIssueCauseUri() {
        return issueCauseUri;
    }

    public ValidationResult setIssueCauseUri(URI issueCauseUri) {
        this.issueCauseUri = issueCauseUri;
        return this;
    }
}
