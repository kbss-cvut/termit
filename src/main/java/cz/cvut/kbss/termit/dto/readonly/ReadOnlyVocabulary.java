package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_slovnik)
public class ReadOnlyVocabulary implements HasIdentifier, Serializable {

    @Id
    private URI uri;

    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private String label;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private String description;

    @OWLObjectProperty(iri = Vocabulary.s_p_importuje_slovnik, fetch = FetchType.EAGER)
    private Set<URI> importedVocabularies;

    public ReadOnlyVocabulary() {
    }

    public ReadOnlyVocabulary(cz.cvut.kbss.termit.model.Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        this.uri = vocabulary.getUri();
        this.label = vocabulary.getLabel();
        this.description = vocabulary.getDescription();
        if (vocabulary.getImportedVocabularies() != null) {
            this.importedVocabularies = new HashSet<>(vocabulary.getImportedVocabularies());
        }
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<URI> getImportedVocabularies() {
        return importedVocabularies;
    }

    public void setImportedVocabularies(Set<URI> importedVocabularies) {
        this.importedVocabularies = importedVocabularies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadOnlyVocabulary)) {
            return false;
        }
        ReadOnlyVocabulary that = (ReadOnlyVocabulary) o;
        return Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public String toString() {
        return "ReadOnlyVocabulary{" + label +
                " <" + uri +
                ">, importedVocabularies=" + importedVocabularies +
                '}';
    }
}
