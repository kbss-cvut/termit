/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.model.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.Term;
import org.topbraid.shacl.vocabulary.SH;

import java.net.URI;
import java.util.Objects;

@NonEntity
@OWLClass(iri = SH.BASE_URI + "ValidationResult")
public class ValidationResult {

    @Id(generated = true)
    private URI id;

    /**
     * Identifier of the affected term.
     */
    @OWLObjectProperty(iri = SH.BASE_URI + "focusNode")
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

    /**
     * Identifier of the cause of the issue.
     */
    @OWLObjectProperty(iri = SH.BASE_URI + "resultPath")
    private URI resultPath;


    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

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

    public URI getResultPath() {
        return resultPath;
    }

    public ValidationResult setResultPath(URI resultPath) {
        this.resultPath = resultPath;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Term term)) {
            return false;
        }
        return Objects.equals(getId(), term.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
