package cz.cvut.kbss.termit.dto.workspace;

import cz.cvut.kbss.termit.exception.workspace.VocabularyNotInWorkspaceException;
import cz.cvut.kbss.termit.model.Workspace;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WorkspaceMetadata {

    private Workspace workspace;

    /**
     * Vocabulary identifier -> vocabulary info
     */
    private Map<URI, VocabularyInfo> vocabularies = new HashMap<>();

    public WorkspaceMetadata() {
    }

    public WorkspaceMetadata(Workspace workspace) {
        this.workspace = workspace;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public Map<URI, VocabularyInfo> getVocabularies() {
        return vocabularies;
    }

    public void setVocabularies(Map<URI, VocabularyInfo> vocabularies) {
        this.vocabularies = vocabularies;
    }

    public VocabularyInfo getVocabularyInfo(URI vocabularyId) {
        Objects.requireNonNull(vocabularyId);
        assert vocabularies != null;
        if (!vocabularies.containsKey(vocabularyId)) {
            throw VocabularyNotInWorkspaceException.create(vocabularyId, workspace);
        }
        return vocabularies.get(vocabularyId);
    }

    @Override
    public String toString() {
        return "WorkspaceMetadata{" +
                "workspace=" + workspace +
                ", vocabularies=" + vocabularies +
                '}';
    }
}
