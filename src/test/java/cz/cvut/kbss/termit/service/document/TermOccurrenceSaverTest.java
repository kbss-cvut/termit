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
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class TermOccurrenceSaverTest {

    @Mock
    private TermOccurrenceDao occurrenceDao;

    @InjectMocks
    private TermOccurrenceSaver sut;

    @Test
    void saveOccurrencesRemovesAllExistingOccurrencesAndPersistsSpecifiedOnes() {
        final Term t = Generator.generateTermWithId();
        final File asset = Generator.generateFileWithId("test.html");
        final List<TermOccurrence> occurrences = List.of(
                Generator.generateTermOccurrence(t, asset, true),
                Generator.generateTermOccurrence(t, asset, true)
        );
        sut.saveOccurrences(occurrences, asset);

        final InOrder inOrder = inOrder(occurrenceDao);
        inOrder.verify(occurrenceDao).removeAll(asset);
        occurrences.forEach(to -> inOrder.verify(occurrenceDao).persist(to));
    }
}
