package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private WorkspaceDao sut;

    @Test
    void findWorkspaceVocabularyMetadataRetrievesVocabularyInfoInstancesPointingToContextsInWhichVocabulariesAreStored() {
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        final Set<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                      .collect(Collectors.toSet());
        transactional(() -> {
            em.persist(ws, new EntityDescriptor(ws.getUri()));
            vocabularies.forEach(v -> em.persist(v, new EntityDescriptor(v.getUri())));
            generateWorkspaceReferences(vocabularies, ws);
        });

        final List<VocabularyInfo> result = sut.findWorkspaceVocabularyMetadata(ws);
        assertFalse(result.isEmpty());
        result.forEach(vi -> {
            assertTrue(vocabularies.stream().anyMatch(v -> v.getUri().equals(vi.getUri())));
            assertEquals(vi.getUri(), vi.getContext()); // For simplicity, we use the vocabulary IRI for context as well
        });
    }

    private void generateWorkspaceReferences(Collection<Vocabulary> vocabularies, Workspace workspace) {
        try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
            conn.begin();
            conn.add(WorkspaceGenerator.generateWorkspaceReferences(vocabularies, workspace));
            conn.commit();
        }
    }

    @Test
    void findWorkspaceVocabularyMetadataRetrievesVocabularyInfoInstancesPointingAlsoToChangeTrackingContext() {
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        final Set<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                      .collect(Collectors.toSet());
        transactional(() -> {
            em.persist(ws, new EntityDescriptor(ws.getUri()));
            vocabularies.forEach(v -> em.persist(v, new EntityDescriptor(v.getUri())));
            generateWorkspaceReferences(vocabularies, ws);
            generateChangeTrackingContextReferences(vocabularies, ws);
        });

        final List<VocabularyInfo> result = sut.findWorkspaceVocabularyMetadata(ws);
        assertFalse(result.isEmpty());
        result.forEach(v -> {
            assertNotNull(v.getContext());
            assertEquals(URI.create(v.getContext().toString() + "/changes"), v.getChangeTrackingContext());
        });
    }

    private void generateChangeTrackingContextReferences(Collection<Vocabulary> vocabularies, Workspace ws) {
        try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            final IRI wsIri = vf.createIRI(ws.getUri().toString());
            final IRI hasChangeTrackingCtx = vf
                    .createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_kontext_sledovani_zmen);
            conn.begin();
            vocabularies.forEach(v -> {
                final IRI changeTrackingCtx = vf.createIRI(v.getUri().toString() + "/changes");
                conn.add(vf.createIRI(v.getUri().toString()), hasChangeTrackingCtx,
                        changeTrackingCtx, wsIri);
                conn.add(changeTrackingCtx, RDF.TYPE,
                        vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_c_kontext_sledovani_zmen));
            });
            conn.commit();
        }
    }
}
