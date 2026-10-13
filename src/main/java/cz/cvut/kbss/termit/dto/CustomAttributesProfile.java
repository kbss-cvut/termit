package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.util.Vocabulary;

import jakarta.validation.constraints.NotEmpty;

import java.util.HashSet;
import java.util.Set;

@NonEntity
@OWLClass(iri = Vocabulary.s_c_custom_attribute_profile)
public class CustomAttributesProfile extends CustomAttributesProfileDescription {

    @NotEmpty
    @OWLObjectProperty(iri = Vocabulary.s_p_has_custom_attribute)
    private Set<CustomAttribute> customAttributes;

    public CustomAttributesProfile() {}

    public CustomAttributesProfile(
            CustomAttributesProfileDescription profileDescription, Set<CustomAttribute> customAttributes) {
        super(profileDescription);
        this.customAttributes = customAttributes;
    }

    public Set<CustomAttribute> getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(Set<CustomAttribute> customAttributes) {
        this.customAttributes = customAttributes;
    }

    public void addCustomAttribute(CustomAttribute customAttribute) {
        if (this.customAttributes == null) {
            this.customAttributes = new HashSet<>();
        }
        this.customAttributes.add(customAttribute);
    }
}
