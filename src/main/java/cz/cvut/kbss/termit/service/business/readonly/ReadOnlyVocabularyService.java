/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
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
