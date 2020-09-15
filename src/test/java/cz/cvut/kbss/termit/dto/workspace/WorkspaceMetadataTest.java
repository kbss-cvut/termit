package cz.cvut.kbss.termit.dto.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.exception.workspace.VocabularyNotInWorkspaceException;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void getVocabularyContextsRetrievesContextsOfAllVocabulariesInWorkspace() {
        final WorkspaceMetadata sut = new WorkspaceMetadata(WorkspaceGenerator.generateWorkspace());
        final VocabularyInfo dataOne = new VocabularyInfo(Generator.generateVocabularyWithId().getUri(),
                Generator.generateUri(), Generator.generateUri());
        final VocabularyInfo dataTwo = new VocabularyInfo(Generator.generateVocabularyWithId().getUri(),
                Generator.generateUri(), Generator.generateUri());
        final VocabularyInfo dataThree = new VocabularyInfo(Generator.generateVocabularyWithId().getUri(),
                Generator.generateUri(), Generator.generateUri());
        sut.setVocabularies(new HashMap<>());
        sut.getVocabularies().put(dataOne.getUri(), dataOne);
        sut.getVocabularies().put(dataTwo.getUri(), dataTwo);
        sut.getVocabularies().put(dataThree.getUri(), dataThree);

        final Set<URI> result = sut.getVocabularyContexts();
        assertNotNull(result);
        assertEquals(3, result.size());
        assertThat(result, hasItems(dataOne.getContext(), dataTwo.getContext(), dataThree.getContext()));
    }
}
