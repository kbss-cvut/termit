package cz.cvut.kbss.termit.model.collection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.MappedSuperclass;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Sparql;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.model.util.validation.HasPrimaryLanguage;
import cz.cvut.kbss.termit.validation.PrimaryNotBlank;

import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.Collection;
import java.util.Set;

@MappedSuperclass
@PrimaryNotBlank("label")
@OWLClass(iri = SKOS.COLLECTION)
public abstract class SkosCollection<T extends Collection<URI>> extends Asset<MultilingualString>
        implements HasTypes, HasPrimaryLanguage {

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private MultilingualString description;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = SKOS.IN_SCHEME)
    private URI vocabulary;

    @NotNull
    @JsonIgnore
    @Sparql(query = "PREFIX dcterms: <" + DC.Terms.NAMESPACE + ">\n" + """
            SELECT DISTINCT ?lang WHERE {
                ?vocabulary dcterms:language ?lang .
            }
            """)
    private String primaryLanguage;

    @Types
    private Set<String> types;

    public abstract T getMembers();

    public abstract void setMembers(T members);

    public void addMember(URI member) {
        getMembers().add(member);
    }

    public void removeMember(URI member) {
        getMembers().remove(member);
    }

    public boolean hasMember(URI member) {
        return getMembers().contains(member);
    }

    @Override
    public MultilingualString getLabel() {
        return label;
    }

    @Override
    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    public MultilingualString getDescription() {
        return description;
    }

    public void setDescription(MultilingualString description) {
        this.description = description;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }
}
