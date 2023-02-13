package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

@OWLClass(iri = Vocabulary.s_c_slovnik)
public class ReadOnlyVocabulary extends Asset<String> implements HasIdentifier, Serializable {

    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private String label;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private String description;

    @OWLObjectProperty(iri = Vocabulary.s_p_importuje_slovnik, fetch = FetchType.EAGER)
    private Set<URI> importedVocabularies;

    public ReadOnlyVocabulary() {
        // Public no-arg constructor for marshalling support
    }

    public ReadOnlyVocabulary(cz.cvut.kbss.termit.model.Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        setUri(vocabulary.getUri());
        this.label = vocabulary.getLabel();
        this.description = vocabulary.getDescription();
        if (vocabulary.getImportedVocabularies() != null) {
            this.importedVocabularies = new HashSet<>(vocabulary.getImportedVocabularies());
        }
    }

    public ReadOnlyVocabulary(VocabularyDto vocabularyDto) {
        Objects.requireNonNull(vocabularyDto);
        setUri(vocabularyDto.getUri());
        this.label = vocabularyDto.getLabel();
        this.description = vocabularyDto.getDescription();
        if (vocabularyDto.getImportedVocabularies() != null) {
            this.importedVocabularies = new HashSet<>(vocabularyDto.getImportedVocabularies());
        }
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
        return Objects.equals(getUri(), that.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri());
    }

    @Override
    public String toString() {
        return "ReadOnlyVocabulary{" + label +
                " " + uriToString(getUri()) +
                ", importedVocabularies=" + importedVocabularies +
                '}';
    }
}
