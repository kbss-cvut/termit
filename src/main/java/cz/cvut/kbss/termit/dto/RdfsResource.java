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

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Utils;

import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Representation of any RDFS resource.
 */
@SparqlResultSetMapping(name = "RdfsResource", classes = {@ConstructorResult(targetClass = RdfsResource.class,
                                                                             variables = {
                                                                                     @VariableResult(name = "x",
                                                                                                     type = URI.class),
                                                                                     @VariableResult(name = "label",
                                                                                                     type = LangString.class),
                                                                                     @VariableResult(name = "comment",
                                                                                                     type = LangString.class),
                                                                                     @VariableResult(name = "type",
                                                                                                     type = String.class)
                                                                             })})
@OWLClass(iri = RDFS.RESOURCE)
public class RdfsResource implements Serializable, HasIdentifier, HasTypes {

    @Id
    private URI uri;

    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = RDFS.COMMENT)
    private MultilingualString comment;

    @Types
    private Set<String> types;

    public RdfsResource() {
    }

    public RdfsResource(URI uri, LangString label, LangString comment, String type) {
        this.uri = uri;
        if (label != null) {
            this.label = MultilingualString.create(label.getValue(), label.getLanguage().orElse(null));
        }
        if (comment != null) {
            this.comment = MultilingualString.create(comment.getValue(), comment.getLanguage().orElse(null));
        }
        this.types = new HashSet<>(Collections.singleton(type));
    }

    public RdfsResource(URI uri, MultilingualString label, MultilingualString comment, String type) {
        this.uri = uri;
        this.label = label;
        this.comment = comment;
        this.types = new HashSet<>(Collections.singleton(type));
    }

    public RdfsResource(RdfsResource other) {
        this.uri = other.uri;
        assert other.label != null;
        this.label = new MultilingualString(other.label.getValue());
        if (other.comment != null) {
            this.comment = new MultilingualString(other.comment.getValue());
        }
        this.types = new HashSet<>(Utils.emptyIfNull(other.types));
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public MultilingualString getLabel() {
        return label;
    }

    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    public MultilingualString getComment() {
        return comment;
    }

    public void setComment(MultilingualString comment) {
        this.comment = comment;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RdfsResource that)) {
            return false;
        }
        return Objects.equals(uri, that.uri) &&
                Objects.equals(label, that.label) &&
                Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, label, types);
    }

    @Override
    public String toString() {
        return "RdfsResource{" +
                "uri=" + uri +
                ", label=" + label +
                ", types=" + types +
                '}';
    }
}
