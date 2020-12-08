package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReadOnlyVocabularyService {

    private final VocabularyService vocabularyService;

    @Autowired
    public ReadOnlyVocabularyService(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    public List<ReadOnlyVocabulary> findAll() {
        return vocabularyService.findAll().stream().map(ReadOnlyVocabulary::new).collect(Collectors.toList());
    }

    public ReadOnlyVocabulary findRequired(URI uri) {
        return new ReadOnlyVocabulary(vocabularyService.findRequired(uri));
    }

    public Collection<URI> getTransitiveDependencies(ReadOnlyVocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        final Vocabulary voc = new Vocabulary();
        voc.setUri(vocabulary.getUri());
        return vocabularyService.getTransitiveDependencies(voc);
    }
}
