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
package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Inferred;
import cz.cvut.kbss.jopa.model.annotations.MappedSuperclass;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Sparql;
import cz.cvut.kbss.jopa.model.annotations.Transient;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.model.util.SupportsSnapshots;
import cz.cvut.kbss.termit.model.util.validation.HasPrimaryLanguage;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import cz.cvut.kbss.termit.validation.PrimaryNotBlank;

import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@PrimaryNotBlank({"label"})
@MappedSuperclass
public abstract class AbstractTerm extends Asset<MultilingualString>
        implements HasTypes, SupportsSnapshots, HasPrimaryLanguage, Serializable {

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = SKOS.DEFINITION)
    private MultilingualString definition;

    @JsonIgnore
    @Transient
    @Sparql(query = "PREFIX dcterms: <" + DC.Terms.NAMESPACE + ">\n" +
            """
            SELECT ?lang WHERE {
                ?vocabulary dcterms:language ?lang .
            }
            """)
    private String primaryLanguage;

    @Transient  // Not used by JOPA
    @OWLObjectProperty(iri = SKOS.NARROWER) // But map the property for JSON-LD serialization
    private Set<TermInfo> subTerms;

    @OWLObjectProperty(iri = SKOS.IN_SCHEME)
    private URI glossary;

    @Inferred
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_stav_pojmu)
    private URI state;

    @Types
    private Set<String> types;

    public AbstractTerm() {
    }

    protected AbstractTerm(AbstractTerm other) {
        Objects.requireNonNull(other);
        setUri(other.getUri());
        if (other.getLabel() != null) {
            this.label = new MultilingualString(other.getLabel().getValue());
        }
        if (other.getDefinition() != null) {
            this.definition = new MultilingualString(other.getDefinition().getValue());
        }
        this.primaryLanguage = other.primaryLanguage;
        this.state = other.state;
        this.glossary = other.glossary;
        this.vocabulary = other.vocabulary;
        if (other.getSubTerms() != null) {
            this.subTerms = other.getSubTerms().stream().map(TermInfo::new)
                                 .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (other.getTypes() != null) {
            this.types = new HashSet<>(other.getTypes());
        }
    }

    @Override
    public MultilingualString getLabel() {
        return label;
    }

    public String getLabel(String language) {
        return this.getLabel() != null ? this.getLabel().get(language) : null;
    }

    public void setLabel(String language, String label) {
        if (this.getLabel() == null) {
            this.setLabel(MultilingualString.create(label, language));
        } else {
            this.getLabel().set(language, label);
        }
    }

    @Override
    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    public MultilingualString getDefinition() {
        return definition;
    }

    public void setDefinition(MultilingualString definition) {
        this.definition = definition;
    }

    @Override
    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    /**
     * Setting the value has no persistent effect, the attribute is resolved
     * from the vocabulary language when the entity is loaded.
     */
    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }

    public Set<TermInfo> getSubTerms() {
        return subTerms;
    }

    public void setSubTerms(Set<TermInfo> subTerms) {
        this.subTerms = subTerms;
    }

    public URI getGlossary() {
        return glossary;
    }

    public void setGlossary(URI glossary) {
        this.glossary = glossary;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    public URI getState() {
        return state;
    }

    public void setState(URI state) {
        this.state = state;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @JsonIgnore
    @Override
    public boolean isSnapshot() {
        return hasType(Vocabulary.s_c_verze_pojmu);
    }

    @Override
    public void accept(AssetVisitor visitor) {
        visitor.visitTerm(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractTerm that)) {
            return false;
        }
        return Objects.equals(getUri(), that.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                getLabel() + ' ' +
                Utils.uriToString(getUri()) +
                ", types=" + getTypes() +
                '}';
    }
}
