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
import cz.cvut.kbss.jopa.exception.LazyLoadingException;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.CascadeType;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.changetracking.Audited;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.model.util.SupportsSnapshots;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.validation.PrimaryNotBlank;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Audited
@OWLClass(iri = cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik)
@JsonLdAttributeOrder({"uri", "label", "description"})
public class Vocabulary extends Asset<MultilingualString> implements HasTypes, SupportsSnapshots, Serializable {

    @PrimaryNotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private MultilingualString description;

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_popisuje_dokument, fetch = FetchType.EAGER)
    private Document document;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar,
                       cascade = {CascadeType.PERSIST, CascadeType.REMOVE},
                       fetch = FetchType.EAGER)
    private Glossary glossary;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_model,
                       cascade = {CascadeType.PERSIST, CascadeType.REMOVE},
                       fetch = FetchType.EAGER)
    private Model model;

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik, fetch = FetchType.EAGER)
    private Set<URI> importedVocabularies;

    @JsonIgnore
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_seznam_rizeni_pristupu, fetch = FetchType.EAGER)
    private URI acl;

    @Properties(fetchType = FetchType.EAGER)
    private Map<String, Set<String>> properties;

    @Types
    private Set<String> types;

    public Vocabulary() {
    }

    public Vocabulary(URI uri) {
        setUri(uri);
    }

    @Override
    public MultilingualString getLabel() {
        return label;
    }

    @Override
    public void setLabel(MultilingualString label) {
        this.label = label;
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

    public MultilingualString getDescription() {
        return description;
    }

    public void setDescription(MultilingualString description) {
        this.description = description;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public Glossary getGlossary() {
        return glossary;
    }

    public void setGlossary(Glossary glossary) {
        this.glossary = glossary;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public Set<URI> getImportedVocabularies() {
        return importedVocabularies;
    }

    public void setImportedVocabularies(Set<URI> importedVocabularies) {
        this.importedVocabularies = importedVocabularies;
    }

    public URI getAcl() {
        return acl;
    }

    public void setAcl(URI acl) {
        this.acl = acl;
    }

    public Map<String, Set<String>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Set<String>> properties) {
        this.properties = properties;
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
    public boolean isSnapshot() {
        return hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
    }

    @Override
    public void accept(AssetVisitor visitor) {
        visitor.visitVocabulary(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vocabulary that)) {
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
        String result = "Vocabulary{"+
                getLabel() + " "
                + Utils.uriToString(getUri());
        try {
            result += ", glossary=" + glossary;
            if (importedVocabularies != null) {
                result +=", importedVocabularies = [" +
                        importedVocabularies.stream().map(Utils::uriToString)
                                            .collect(Collectors.joining(", ")) + "]";
            }
        } catch (LazyLoadingException e) {
            // persistent context not available
        }

        return result;
    }
}
