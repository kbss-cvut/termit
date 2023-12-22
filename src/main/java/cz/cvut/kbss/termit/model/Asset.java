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
package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.MappedSuperclass;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.validation.WithoutQueryParameters;

import java.net.URI;

/**
 * Represents basic info about an asset managed by the application.
 *
 * @param <T> Type of the label
 */
@MappedSuperclass
public abstract class Asset<T> implements HasIdentifier {

    @WithoutQueryParameters
    @Id
    private URI uri;

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public abstract T getLabel();

    public abstract void setLabel(T label);

    @JsonIgnore
    public String getPrimaryLabel() {
        return getLabel().toString();
    }

    public void accept(AssetVisitor visitor) {
        // Do nothing by default. Relevant subclasses should provide code invoking the visitor
    }
}
