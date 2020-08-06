package cz.cvut.kbss.termit.dto.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.exception.workspace.VocabularyNotInWorkspaceException;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceMetadataTest {

    @Test
    void getVocabularyInfoRetrievesMetadataOfVocabularyWithSpecifiedUri() {
        final WorkspaceMetadata sut = new WorkspaceMetadata(WorkspaceGenerator.generateWorkspace());
        final Vocabulary voc = Generator.generateVocabularyWithId();
        final VocabularyInfo data = new VocabularyInfo(voc.getUri(), Generator.generateUri(), Generator.generateUri());
        sut.setVocabularies(Collections.singletonMap(voc.getUri(), data));
        assertEquals(data, sut.getVocabularyInfo(voc.getUri()));
    }

    @Test
    void getVocabularyInfoThrowsVocabularyNotInWorkspaceExceptionWhenVocabularyIsNotFoundInWorkspaceMetadata() {
        final WorkspaceMetadata sut = new WorkspaceMetadata(WorkspaceGenerator.generateWorkspace());
        assertThrows(VocabularyNotInWorkspaceException.class, () -> sut.getVocabularyInfo(Generator.generateUri()));
    }
}
