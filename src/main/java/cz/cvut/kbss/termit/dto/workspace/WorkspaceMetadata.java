package cz.cvut.kbss.termit.dto.workspace;

import cz.cvut.kbss.termit.exception.workspace.VocabularyNotInWorkspaceException;
import cz.cvut.kbss.termit.model.Workspace;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Gets workspace-related data about vocabulary with the specified identifier.
     * <p>
     * This includes the contexts in which the vocabulary is stored in this context, its change tracking context etc.
     *
     * @param vocabularyId Vocabulary identifier
     * @return VocabularyInfo instance
     * @throws VocabularyNotInWorkspaceException When no such vocabulary is in this workspace
     */
    public VocabularyInfo getVocabularyInfo(URI vocabularyId) {
        Objects.requireNonNull(vocabularyId);
        assert vocabularies != null;
        if (!vocabularies.containsKey(vocabularyId)) {
            throw VocabularyNotInWorkspaceException.create(vocabularyId, workspace);
        }
        return vocabularies.get(vocabularyId);
    }

    /**
     * Gets the set of contexts in which the vocabularies in this workspace are stored.
     * <p>
     * Only the main vocabulary contexts are retrieved, auxiliary contexts like change tracking are not included.
     *
     * @return Set of vocabulary contexts
     */
    public Set<URI> getVocabularyContexts() {
        return vocabularies.values().stream().map(VocabularyInfo::getContext).collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "WorkspaceMetadata{" +
                "workspace=" + workspace +
                ", vocabularies=" + vocabularies +
                '}';
    }
}
