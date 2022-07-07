package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReadOnlyVocabularyService implements SnapshotProvider<ReadOnlyVocabulary> {

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

    public Collection<URI> getTransitivelyImportedVocabularies(ReadOnlyVocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return vocabularyService.getTransitivelyImportedVocabularies(new Vocabulary(vocabulary.getUri()));
    }

    public List<Snapshot> findSnapshots(ReadOnlyVocabulary asset) {
        Objects.requireNonNull(asset);
        return vocabularyService.findSnapshots(new Vocabulary(asset.getUri()));
    }

    public ReadOnlyVocabulary findVersionValidAt(ReadOnlyVocabulary asset, Instant at) {
        Objects.requireNonNull(asset);
        return new ReadOnlyVocabulary(vocabularyService.findVersionValidAt(new Vocabulary(asset.getUri()), at));
    }
}
