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
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_glosar)
public class Glossary extends AbstractEntity {

    /**
     * This attribute should contain only root terms. The term hierarchy is modelled by terms having sub-terms, so all
     * terms should be reachable.
     */
    @OWLObjectProperty(iri = SKOS.HAS_TOP_CONCEPT)
    private Set<URI> rootTerms;

    public Set<URI> getRootTerms() {
        return rootTerms;
    }

    public void setRootTerms(Set<URI> rootTerms) {
        this.rootTerms = rootTerms;
    }

    /**
     * Adds the specified root term into this glossary.
     *
     * @param rootTerm Term to add
     */
    public void addRootTerm(Term rootTerm) {
        Objects.requireNonNull(rootTerm);
        if (rootTerms == null) {
            this.rootTerms = new HashSet<>();
        }
        rootTerms.add(rootTerm.getUri());
    }

    /**
     * Removes the specified term from root terms of this glossary, if it were present.
     *
     * @param toRemove The term to remove from root terms
     */
    public void removeRootTerm(Term toRemove) {
        Objects.requireNonNull(toRemove);
        if (rootTerms != null) {
            rootTerms.remove(toRemove.getUri());
        }
    }

    @Override
    public String toString() {
        return "Glossary{" +
                "term count=" + (rootTerms != null ? rootTerms.size() : 0) +
                " " + super.toString() + "}";
    }
}
