package cz.cvut.kbss.termit.dto.listing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO for term listing.
 * <p>
 * Contains fewer data than a regular {@link cz.cvut.kbss.termit.model.Term}.
 */
@OWLClass(iri = SKOS.CONCEPT)
@JsonLdAttributeOrder({"uri", "label", "subTerms"})
@JsonIgnoreProperties({"definition"})
public class TermDto extends AbstractTerm {

    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER)
    private Set<TermDto> parentTerms;

    public TermDto() {
    }

    public TermDto(Term other) {
        super(other);
        if (other.getParentTerms() != null) {
            setParentTerms(other.getParentTerms().stream().map(TermDto::new).collect(Collectors.toSet()));
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
