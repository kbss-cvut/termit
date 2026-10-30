package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.validation.WithoutQueryParameters;
import cz.cvut.kbss.termit.util.Vocabulary;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.net.URI;

@NonEntity
@OWLClass(iri = Vocabulary.s_c_custom_attribute_profile_description)
public class CustomAttributesProfileDescription implements HasIdentifier, Serializable {

    @Id
    @NotNull
    @WithoutQueryParameters
    private URI uri;

    @NotNull
    @OWLDataProperty(iri = Vocabulary.s_p_title)
    private MultilingualString title;

    @OWLDataProperty(iri = Vocabulary.s_p_description)
    private MultilingualString description;

    public CustomAttributesProfileDescription() {}

    public CustomAttributesProfileDescription(CustomAttributesProfileDescription other) {
        this.uri = other.uri;
        this.title = other.title;
        this.description = other.description;
    }

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
}
