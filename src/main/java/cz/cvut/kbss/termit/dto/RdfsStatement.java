/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.RDF;

import java.io.Serializable;
import java.net.URI;

/**
 * Utility class describing a generic {@link #relation} between an {@link #object} and {@link #subject}
 */
@NonEntity
@OWLClass(iri = RDF.STATEMENT)
@SparqlResultSetMapping(name = "RDFStatement",
                        classes = {@ConstructorResult(targetClass = RdfsStatement.class,
                                                      variables = {
                                                              @VariableResult(name = "object", type = URI.class),
                                                              @VariableResult(name = "relation", type = URI.class),
                                                              @VariableResult(name = "subject", type = URI.class),
                                                      })})
public class RdfsStatement implements Serializable {

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = RDF.OBJECT)
    private URI object;

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = RDF.PREDICATE)
    private URI relation;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = RDF.SUBJECT)
    private URI subject;

    public RdfsStatement() {
    }

    public RdfsStatement(URI object, URI relation, URI subject) {
        this.object = object;
        this.relation = relation;
        this.subject = subject;
    }

    public URI getObject() {
        return object;
    }

    public void setObject(URI object) {
        this.object = object;
    }

    public URI getRelation() {
        return relation;
    }

    public void setRelation(URI relation) {
        this.relation = relation;
    }

    public URI getSubject() {
        return subject;
    }

    public void setSubject(URI subject) {
        this.subject = subject;
    }
}
