package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.TermService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReadOnlyTermService {

    private final TermService termService;

    @Autowired
    public ReadOnlyTermService(TermService termService) {
        this.termService = termService;
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
        return new ReadOnlyTerm(termService.findRequired(termId));
    }

    public List<ReadOnlyTerm> findSubTerms(ReadOnlyTerm parent) {
        Objects.requireNonNull(parent);
        final Term arg = new Term();
        arg.setUri(parent.getUri());
        if (parent.getSubTerms() != null) {
            arg.setSubTerms(parent.getSubTerms());
        }
        return termService.findSubTerms(arg).stream().map(ReadOnlyTerm::new).collect(Collectors.toList());
    }
}
