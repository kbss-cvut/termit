package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReadOnlyTermService {

    private final TermService termService;

    private Configuration configuration;

    @Autowired
    public ReadOnlyTermService(final TermService termService, final Configuration configuration) {
        this.termService = termService;
        this.configuration = configuration;
    }

    public Vocabulary findVocabularyRequired(URI vocabularyUri) {
        return termService.findVocabularyRequired(vocabularyUri);
    }

    public List<TermDto> findAll(Vocabulary vocabulary) {
        return termService.findAll(vocabulary).stream().map(TermDto::new).collect(Collectors.toList());
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
        Set<String> properties = configuration.getPublicView().getWhiteListProperties();
        if ( properties == null) {
            properties = new HashSet<>();
        }
        return new ReadOnlyTerm(term, properties);
    }

    public List<ReadOnlyTerm> findSubTerms(ReadOnlyTerm parent) {
        Objects.requireNonNull(parent);
        final Term arg = new Term();
        arg.setUri(parent.getUri());
        if (parent.getSubTerms() != null) {
            arg.setSubTerms(parent.getSubTerms());
        }
        return termService.findSubTerms(arg).stream().map(this::create).collect(Collectors.toList());
    }

    public List<Comment> getComments(Term term) {
        return termService.getComments(term);
    }

    public Term getRequiredReference(URI uri) {
        return termService.getRequiredReference(uri);
    }
}
