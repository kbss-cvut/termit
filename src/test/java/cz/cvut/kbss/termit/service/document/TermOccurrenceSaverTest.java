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
