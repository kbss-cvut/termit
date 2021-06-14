/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.util;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.util.Assert;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static org.mockito.Mockito.when;

class UtilsTest {

    private static final String BASE = "BASE";

    @Test
    public void getUniqueIriFromBaseReturnsBaseIfCheckFails() {
        Assert.equals(BASE, Utils.getUniqueIriFromBase(BASE, (iri) -> Optional.empty()));
    }

    @Test
    public void getUniqueIriFromBaseReturnsBaseIfCheckSucceeds() {
        final Function f = Mockito.mock(Function.class);
        when(f.apply(BASE)).thenReturn(Optional.of(new Object()));
        when(f.apply(BASE + "-0")).thenReturn(Optional.empty());
        Assert.equals(BASE + "-0", Utils.getUniqueIriFromBase(BASE, f));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriIfAllConceptsHaveTheSameNamespace() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "/pojem";
        conceptIris.add(namespace + "A");
        conceptIris.add(namespace + "B");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriForTermItVocabularies() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "/pojem";
        conceptIris.add(namespace + "A");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriForExternalSlashVocabularies() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "/";
        conceptIris.add(namespace + "A");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriForExternalHashVocabularies() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "#";
        conceptIris.add(namespace + "A");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriThrowsExceptionIfNoConceptIsProvided() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Utils.getVocabularyIri(Collections.emptySet(), "/pojem");
        });
    }

    @Test
    public void getVocabularyIriThrowsExceptionIfConceptsWithDifferentNamespacesAreProvided() {
        final Set<String> conceptIris = new HashSet<>();
        conceptIris.add("https://example.org/pojem/A");
        conceptIris.add("https://example2.org/pojem/B");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Utils.getVocabularyIri(conceptIris, "/pojem");
        });
    }

    @Test
    public void changeIriChangesIriCorrectly() {
        String oldIri = "https://example.org/a";
        String newIri = "https://example.org/b";
        Model model = new DynamicModelFactory().createEmptyModel();
        model.add(Values.iri(oldIri), RDF.TYPE, SKOS.CONCEPT);

        Utils.changeIri(oldIri, newIri, model);

        Assert.equals(1l, getStatementCountWithSubject(model, null));
        Assert.equals(0l, getStatementCountWithSubject(model, Values.iri(oldIri)));
        Assert.equals(1l, getStatementCountWithSubject(model, Values.iri(newIri)));
    }

    @Test
    public void changeNamespaceChangesNamespaceCorrectly() {
        String oldIri = "https://example.org/a";
        String newNamespace = "https://example2.org/";
        Model model = new DynamicModelFactory().createEmptyModel();
        model.add(Values.iri(oldIri), RDF.TYPE, SKOS.CONCEPT);

        Utils.changeNamespace("https://example.org/", newNamespace, model);

        Assert.equals(1l, getStatementCountWithSubject(model, null));
        Assert.equals(0l, getStatementCountWithSubject(model, Values.iri(oldIri)));
        Assert.equals(1l, getStatementCountWithSubject(model, Values.iri(newNamespace + "a")));
    }

    private long getStatementCountWithSubject(Model model, IRI subject) {
        return model.filter(subject, null, null).size();
    }
}
