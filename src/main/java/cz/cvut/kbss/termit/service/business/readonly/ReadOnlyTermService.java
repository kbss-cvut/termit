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
package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class ReadOnlyTermService {

    private final TermService termService;

    private final Configuration configuration;

    @Autowired
    public ReadOnlyTermService(final TermService termService, final Configuration configuration) {
        this.termService = termService;
        this.configuration = configuration;
    }

    public Vocabulary findVocabularyRequired(URI vocabularyUri) {
        return termService.findVocabularyRequired(vocabularyUri);
    }

    public List<TermDto> findAll(Vocabulary vocabulary, Pageable pageSpec) {
        return termService.findAll(vocabulary, pageSpec);
    }

    public List<TermDto> findAll(String searchString, Vocabulary vocabulary, Pageable pageSpec) {
        return termService.findAll(searchString, vocabulary, pageSpec);
    }

    public List<TermDto> findAllIncludingImported(String searchString, Vocabulary vocabulary, Pageable pageSpec) {
        return termService.findAllIncludingImported(searchString, vocabulary, pageSpec);
    }

    public List<TermDto> findAllRoots(Vocabulary vocabulary, Pageable pageSpec) {
        return termService.findAllRoots(vocabulary, pageSpec, Collections.emptyList());
    }

    public List<TermDto> findAllRootsIncludingImported(Vocabulary vocabulary, Pageable pageSpec) {
        return termService.findAllRootsIncludingImported(vocabulary, pageSpec, Collections.emptyList());
    }

    public ReadOnlyTerm findRequired(URI termId) {
        return create(termService.findRequired(termId));
    }

    public TermInfo findRequiredTermInfo(URI termId) {
        return termService.findRequiredTermInfo(termId);
    }

    private ReadOnlyTerm create(final Term term) {
        final Collection<String> properties = Utils.emptyIfNull(configuration.getPublicView().getWhiteListProperties());
        return new ReadOnlyTerm(term, properties);
    }

    public List<TermDto> findSubTerms(ReadOnlyTerm parent) {
        Objects.requireNonNull(parent);
        final Term arg = toTerm(parent);
        return termService.findSubTerms(arg);
    }

    private static Term toTerm(ReadOnlyTerm roTerm) {
        final Term t = new Term(roTerm.getUri());
        // Ensure vocabulary is set on the Term instance as it may be referenced later
        t.setVocabulary(roTerm.getVocabulary());
        return t;
    }

    /**
     * Gets comments related to the specified term created in the specified time interval.
     *
     * @param term Term to get comments for
     * @param from Retrieval interval start
     * @param to   Retrieval interval end
     * @return List of comments
     */
    public List<Comment> getComments(Term term, Instant from, Instant to) {
        return termService.getComments(term, from, to);
    }

    public Term getReference(URI uri) {
        return termService.getReference(uri);
    }

    /**
     * Gets occurrences of the specified term in other terms' definitions.
     *
     * @param instance Term whose definitional occurrences to search for
     * @return List of definitional occurrences of the specified term
     */
    public List<TermOccurrence> getDefinitionallyRelatedOf(Term instance) {
        return termService.getDefinitionallyRelatedOf(instance);
    }

    /**
     * Gets occurrences of terms which appear in the specified term's definition.
     *
     * @param instance Term in whose definition to search for related terms
     * @return List of term occurrences in the specified term's definition
     */
    public List<TermOccurrence> getDefinitionallyRelatedTargeting(Term instance) {
        return termService.getDefinitionallyRelatedTargeting(instance);
    }

    public List<Snapshot> findSnapshots(ReadOnlyTerm asset) {
        Objects.requireNonNull(asset);
        final Term arg = toTerm(asset);
        return termService.findSnapshots(arg);
    }

    public ReadOnlyTerm findVersionValidAt(ReadOnlyTerm asset, Instant at) {
        Objects.requireNonNull(asset);
        final Term arg = toTerm(asset);
        return create(termService.findVersionValidAt(arg, at));
    }
}
