package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.validation.WithoutQueryParameters;
import cz.cvut.kbss.termit.util.Vocabulary;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

@NonEntity
@OWLClass(iri = Vocabulary.s_c_custom_attribute_profile)
public class CustomAttributesProfile implements HasIdentifier, Serializable {
    @Id
    @NotNull
    @WithoutQueryParameters
    private URI uri;

    @NotNull
    @OWLDataProperty(iri = Vocabulary.s_p_title)
    private MultilingualString title;

    @OWLDataProperty(iri = Vocabulary.s_p_description)
    private MultilingualString description;

    @NotEmpty
    @OWLObjectProperty(iri = Vocabulary.s_p_has_custom_attribute)
    private Set<CustomAttribute> customAttributes;

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public MultilingualString getTitle() {
        return title;
    }

    public void setTitle(MultilingualString title) {
        this.title = title;
    }

    public MultilingualString getDescription() {
        return description;
    }

    public void setDescription(MultilingualString description) {
        this.description = description;
    }

    public Set<CustomAttribute> getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(Set<CustomAttribute> customAttributes) {
        this.customAttributes = customAttributes;
    }
}
