package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
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
import java.util.stream.Collectors;

@Service
public class ReadOnlyTermService implements SnapshotProvider<ReadOnlyTerm> {

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

    public List<TermDto> findAll(Vocabulary vocabulary) {
        return termService.findAll(vocabulary);
    }

    public List<TermDto> findAll(String searchString, Vocabulary vocabulary) {
        return termService.findAll(searchString, vocabulary);
    }

    public List<TermDto> findAllIncludingImported(String searchString, Vocabulary vocabulary) {
        return termService.findAllIncludingImported(searchString, vocabulary);
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

    private ReadOnlyTerm create(final Term term) {
        final Collection<String> properties = Utils.emptyIfNull(configuration.getPublicView().getWhiteListProperties());
        return new ReadOnlyTerm(term, properties);
    }

    public List<ReadOnlyTerm> findSubTerms(ReadOnlyTerm parent) {
        Objects.requireNonNull(parent);
        final Term arg = new Term(parent.getUri());
        if (parent.getSubTerms() != null) {
            arg.setSubTerms(parent.getSubTerms());
        }
        return termService.findSubTerms(arg).stream().map(this::create).collect(Collectors.toList());
    }

    /**
     * Gets comments related to the specified term created in the specified time interval.
     *
     * @param term Term to get comments for
     * @param from Retrieval interval start
     * @param to Retrieval interval end
     * @return List of comments
     */
    public List<Comment> getComments(Term term, Instant from, Instant to) {
        return termService.getComments(term, from, to);
    }

    public Term getRequiredReference(URI uri) {
        return termService.getRequiredReference(uri);
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

    @Override
    public List<Snapshot> findSnapshots(ReadOnlyTerm asset) {
        Objects.requireNonNull(asset);
        final Term arg = new Term(asset.getUri());
        return termService.findSnapshots(arg);
    }

    @Override
    public ReadOnlyTerm findVersionValidAt(ReadOnlyTerm asset, Instant at) {
        Objects.requireNonNull(asset);
        final Term arg = new Term(asset.getUri());
        return new ReadOnlyTerm(termService.findVersionValidAt(arg, at));
    }
}
