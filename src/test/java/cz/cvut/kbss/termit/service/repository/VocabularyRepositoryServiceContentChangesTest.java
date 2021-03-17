/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.service.business.TermService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyRepositoryServiceContentChangesTest {

    @InjectMocks
    private VocabularyRepositoryService sut;

    @Mock
    private TermService termService;

    @Test
    void getContentChangesRetrievesChangesOfAllTermsInVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term t1 = Generator.generateTermWithId(vocabulary.getUri());
        final Term t2 = Generator.generateTermWithId(vocabulary.getUri());

        final List<Term> terms = new ArrayList<>();
        terms.add(t1);
        terms.add(t2);
        when(termService.findAll(vocabulary)).thenReturn(terms);

        User user = Generator.generateUser();

        final List<AbstractChangeRecord> ucr1 = Generator.generateChangeRecords(vocabulary, user);
        when(termService.getChanges(t1)).thenReturn(ucr1);

        final List<AbstractChangeRecord> ucr2 = Generator.generateChangeRecords(vocabulary, user);
        when(termService.getChanges(t2)).thenReturn(ucr2);

        final Set<AbstractChangeRecord> all = new HashSet<>();
        all.addAll(ucr1);
        all.addAll(ucr2);

        final List<AbstractChangeRecord> changes = sut.getChangesOfContent(vocabulary);
        assertEquals(all, new HashSet<>(changes));
    }
}
