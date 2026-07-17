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
package cz.cvut.kbss.termit.dto.listing;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Utils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO for term listing.
 * <p>
 * Contains less data than a regular {@link cz.cvut.kbss.termit.model.Term}.
 */
@OWLClass(iri = SKOS.CONCEPT)
@JsonLdAttributeOrder({"uri", "label", "parentTerms", "subTerms"})
public class TermDto extends AbstractTerm {

    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER)
    private Set<TermDto> parentTerms;

    public TermDto() {
    }

    public TermDto(AbstractTerm other) {
        super(other);
    }

    public TermDto(Term other) {
        super(other);
        if (other.getParentTerms() != null) {
            setParentTerms(other.getParentTerms().stream().map(ti -> {
                final TermDto result = new TermDto();
                result.setUri(ti.getUri());
                result.setLabel(ti.getLabel());
                result.setVocabulary(ti.getVocabulary());
                result.setState(ti.getState());
                result.setTypes(new HashSet<>(Utils.emptyIfNull(ti.getTypes())));
                return result;
            }).collect(Collectors.toSet()));
        }
    }

    public Set<TermDto> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<TermDto> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public boolean hasParentTerms() {
        return parentTerms != null && !parentTerms.isEmpty();
    }
}
