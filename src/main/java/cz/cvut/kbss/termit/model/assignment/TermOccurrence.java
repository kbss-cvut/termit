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
package cz.cvut.kbss.termit.model.assignment;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cz.cvut.kbss.jopa.model.annotations.CascadeType;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Transient;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.util.Copyable;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "javaClass")
@OWLClass(iri = Vocabulary.s_c_vyskyt_termu)
public abstract class TermOccurrence extends AbstractEntity implements Copyable<TermOccurrence>, HasTypes {

    /**
     * Suffix used to identify term occurrence contexts (named graphs) in the repository.
     */
    public static final String CONTEXT_SUFFIX = "occurrences";

    @NotNull
    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_je_prirazenim_termu)
    private URI term;

    @NotNull
    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_cil, cascade = {CascadeType.MERGE}, fetch = FetchType.EAGER)
    OccurrenceTarget target;

    @OWLDataProperty(iri = DC.Terms.DESCRIPTION)
    private String description;

    @Types
    private Set<String> types;

    @Transient
    private Double score;

    /**
     * Value of the {@literal about} attribute of the HTML element representing the occurrence.
     */
    @Transient
    @OWLDataProperty(iri = DC.Terms.IDENTIFIER)
    private String elementAbout;

    public TermOccurrence() {
    }

    public TermOccurrence(URI term, OccurrenceTarget target) {
        this.term = Objects.requireNonNull(term);
        this.target = Objects.requireNonNull(target);
    }

    public URI getTerm() {
        return term;
    }

    public void setTerm(URI term) {
        this.term = term;
    }

    public OccurrenceTarget getTarget() {
        return target;
    }

    public void setTarget(OccurrenceTarget target) {
        this.target = target;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    /**
     * Represents the score of the corresponding text match.
     * <p>
     * Relevant only in case of occurrences resolved by the annotation service.
     *
     * @return Match score, possibly {@code null} (if score was not determined or the occurrence was created manually)
     */
    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getElementAbout() {
        return elementAbout;
    }

    public void setElementAbout(String elementAbout) {
        this.elementAbout = elementAbout;
    }

    /**
     * Determines the value of the {@literal about} attribute of the HTML element representing this occurrence.
     * <p>
     * The value is derived from the URI of the occurrence and a blank node prefix is prepended to it.
     * <p>
     * The value is also assigned to the field {@link #elementAbout} for further use.
     *
     * @return Value of the {@literal about} attribute
     */
    public String resolveElementAbout() {
        final String strIri = getUri().toString();
        this.elementAbout = Constants.BNODE_PREFIX + strIri.substring(strIri.lastIndexOf('/') + 1);
        return elementAbout;
    }

    /**
     * Marks this term occurrence as suggested by automation.
     * <p>
     * Corresponds to classifying with {@link Vocabulary#s_c_navrzeny_vyskyt_termu}.
     */
    public void markSuggested() {
        addType(Vocabulary.s_c_navrzeny_vyskyt_termu);
    }

    /**
     * Marks this term occurrence as approved.
     * <p>
     * Corresponds to removing the {@link Vocabulary#s_c_navrzeny_vyskyt_termu} type.
     */
    public void markApproved() {
        removeType(Vocabulary.s_c_navrzeny_vyskyt_termu);
    }

    /**
     * Checks whether this term occurrence is marked as suggested by automation.
     * <p>
     * Suggested in this context means classified with {@link Vocabulary#s_c_navrzeny_vyskyt_termu}.
     *
     * @return {@code true} when this instance is marked as suggested by automation
     */
    public boolean isSuggested() {
        return hasType(Vocabulary.s_c_navrzeny_vyskyt_termu);
    }

    @Override
    public String toString() {
        return "TermOccurrence{<" +
                getUri() +
                ">, term=<" + term +
                ">, target=" + target + '}';
    }

    /**
     * Resolves identifier of the repository context in which this occurrence should be stored.
     * <p>
     * The context is based on the target source's identifier and {@link #CONTEXT_SUFFIX}.
     *
     * @return Repository context URI
     * @see #resolveContext(URI)
     */
    public URI resolveContext() {
        return resolveContext(target.getSource());
    }

    /**
     * Resolves identifier of the repository context in which term occurrences targeting the specified source should be
     * stored.
     * <p>
     * The context is based on the specified source and {@link #CONTEXT_SUFFIX}.
     *
     * @return Repository context URI
     */
    public static URI resolveContext(URI source) {
        Objects.requireNonNull(source);
        String strSource = source.toString();
        if (!strSource.endsWith("/")) {
            strSource += "/";
        }
        return URI.create(strSource + CONTEXT_SUFFIX);
    }
}
