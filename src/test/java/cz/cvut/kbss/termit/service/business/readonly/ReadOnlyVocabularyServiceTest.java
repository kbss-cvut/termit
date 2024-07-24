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
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadOnlyVocabularyServiceTest {

    @Mock
    private VocabularyService vocabularyService;

    @InjectMocks
    private ReadOnlyVocabularyService sut;

    @Test
    void findAllReturnsAllVocabulariesTransformedToReadOnlyVersions() {
        final List<VocabularyDto> vocabularies = IntStream.range(0, 5).mapToObj(
                                                                  i -> Environment.getDtoMapper().vocabularyToVocabularyDto(Generator.generateVocabularyWithId()))
                                                          .collect(Collectors.toList());
        when(vocabularyService.findAll()).thenReturn(vocabularies);

        final List<ReadOnlyVocabulary> result = sut.findAll();
        assertEquals(vocabularies.size(), result.size());
        result.forEach(r -> assertTrue(vocabularies.stream().anyMatch(v -> v.getUri().equals(r.getUri()))));
    }

    @Test
    void findRequiredRetrievesReadOnlyVersionOfVocabularyWithSpecifiedId() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(vocabularyService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);

        final ReadOnlyVocabulary result = sut.findRequired(vocabulary.getUri());
        assertNotNull(result);
        assertEquals(vocabulary.getUri(), result.getUri());
    }

    @Test
    void findRequiredThrowsNotFoundExceptionWhenNoMatchingVocabularyIsFound() {
        when(vocabularyService.findRequired(any())).thenThrow(NotFoundException.class);
        assertThrows(NotFoundException.class, () -> sut.findRequired(Generator.generateUri()));
    }

    @Test
    void getTransitivelyImportedVocabulariesRetrievesImportedVocabulariesFromVocabularyService() {
        final ReadOnlyVocabulary voc = new ReadOnlyVocabulary(Generator.generateVocabularyWithId());
        final Set<URI> imports = IntStream.range(0, 3).mapToObj(i -> Generator.generateUri())
                                          .collect(Collectors.toSet());
        when(vocabularyService.getTransitivelyImportedVocabularies(any())).thenReturn(imports);

        final Collection<URI> result = sut.getTransitivelyImportedVocabularies(voc);
        assertEquals(imports, result);
        final ArgumentCaptor<Vocabulary> captor = ArgumentCaptor.forClass(Vocabulary.class);
        verify(vocabularyService).getTransitivelyImportedVocabularies(captor.capture());
        assertEquals(voc.getUri(), captor.getValue().getUri());
    }

    @Test
    void findSnapshotsRetrievesSnapshotsOfSpecifiedVocabulary() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        final ReadOnlyVocabulary voc = new ReadOnlyVocabulary(v);
        final List<Snapshot> snapshots = IntStream.range(0, 3).mapToObj(i -> Generator.generateSnapshot(v))
                                                  .collect(Collectors.toList());
        when(vocabularyService.findSnapshots(v)).thenReturn(snapshots);

        final List<Snapshot> result = sut.findSnapshots(voc);
        assertEquals(snapshots, result);
        verify(vocabularyService).findSnapshots(v);
    }

    @Test
    void findVersionValidAtRetrievesVocabularyVersionsValidAtSpecifiedTimestamp() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        final ReadOnlyVocabulary voc = new ReadOnlyVocabulary(v);
        final Vocabulary version = Generator.generateVocabularyWithId();
        final Instant timestamp = Instant.now();
        when(vocabularyService.findVersionValidAt(v, timestamp)).thenReturn(version);

        final ReadOnlyVocabulary result = sut.findVersionValidAt(voc, timestamp);
        assertEquals(new ReadOnlyVocabulary(version), result);
        verify(vocabularyService).findVersionValidAt(v, timestamp);
    }
}
