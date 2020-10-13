/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.environment.config.WorkspaceTestConfig;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;

class VocabularyDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private VocabularyDao sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findAllReturnsVocabulariesOrderedByName() {
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        transactional(() -> vocabularies.forEach(v -> em.persist(v, descriptorFor(v))));

        final List<Vocabulary> result = sut.findAll();
        vocabularies.sort(Comparator.comparing(Vocabulary::getLabel));
        for (int i = 0; i < vocabularies.size(); i++) {
            assertEquals(vocabularies.get(i).getUri(), result.get(i).getUri());
        }
    }

    @Test
    void persistSavesVocabularyIntoContextGivenByItsIri() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> sut.persist(vocabulary));

        final Descriptor descriptor = descriptorFor(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri(), descriptor);
        assertNotNull(result);
    }

    private Descriptor descriptorFor(Vocabulary vocabulary) {
        return descriptorFactory.vocabularyDescriptor(vocabulary);
    }

    @Test
    void updateUpdatesVocabularyInContextGivenByItsIri() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));

        final String newName = "Updated vocabulary name";
        vocabulary.setLabel(newName);
        transactional(() -> sut.update(vocabulary));

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri(), descriptor);
        assertNotNull(result);
        assertEquals(newName, result.getLabel());
    }

    @Test
    void updateEvictsPossiblyPreviouslyLoadedInstanceFromSecondLevelCache() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        // This causes the second level cache to be initialized with the loaded vocabulary (in the default context)
        final List<Vocabulary> vocabularies = sut.findAll();
        assertEquals(1, vocabularies.size());

        final String newName = "Updated vocabulary name";
        vocabulary.setLabel(newName);
        transactional(() -> sut.update(vocabulary));
        final List<Vocabulary> result = sut.findAll();
        assertEquals(1, result.size());
        assertEquals(newName, result.get(0).getLabel());
    }

    @Test
    void updateWorksCorrectlyInContextsForDocumentVocabulary() {
        final DocumentVocabulary vocabulary = new DocumentVocabulary();
        vocabulary.setUri(Generator.generateUri());
        vocabulary.setLabel("test-vocabulary");
        vocabulary.setGlossary(new Glossary());
        vocabulary.setModel(new Model());
        final Document doc = new Document();
        doc.setLabel("test-document");
        doc.setUri(Generator.generateUri());
        final File file = new File();
        file.setLabel("test-file");
        file.setUri(Generator.generateUri());
        doc.addFile(file);
        vocabulary.setDocument(doc);
        final Descriptor vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        final Descriptor docDescriptor = descriptorFactory.documentDescriptor(vocabulary);
        transactional(() -> {
            em.persist(file, docDescriptor);
            em.persist(doc, docDescriptor);
            em.persist(vocabulary, vocabularyDescriptor);
        });

        final String newComment = "New comment";
        vocabulary.setDescription(newComment);
        transactional(() -> sut.update(vocabulary));

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri(), vocabularyDescriptor);
        assertEquals(newComment, result.getDescription());
    }

    @Test
    void updateGlossaryMergesGlossaryIntoPersistenceContext() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        final Term term = Generator.generateTermWithId();
        vocabulary.getGlossary().addRootTerm(term);
        final Descriptor termDescriptor = descriptorFactory.termDescriptor(vocabulary);
        transactional(() -> {
            em.persist(term, termDescriptor);
            sut.updateGlossary(vocabulary);
        });

        transactional(() -> {
            // If we don't run this in transaction, the delegate em is closed right after find and lazy loading of terms
            // does not work
            final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri());
            assertTrue(result.getRootTerms().contains(term.getUri()));
        });
    }

    @Test
    void updateGlossaryMergesGlossaryIntoCorrectRepositoryContext() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        final Term term = Generator.generateTermWithId();
        vocabulary.getGlossary().addRootTerm(term);
        final Descriptor termDescriptor = descriptorFactory.termDescriptor(vocabulary);
        transactional(() -> {
            em.persist(term, termDescriptor);
            sut.updateGlossary(vocabulary);
        });

        final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri(),
                descriptorFactory.glossaryDescriptor(vocabulary));
        assertTrue(result.getRootTerms().contains(term.getUri()));
    }

    @Test
    void updateGlossaryReturnsManagedGlossaryInstance() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        transactional(() -> {
            final Glossary merged = sut.updateGlossary(vocabulary);
            assertTrue(em.contains(merged));
        });
    }

    @Test
    void hasInterVocabularyTermRelationshipsReturnsFalseForVocabulariesWithoutSKOSRelatedTerms() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
        });

        assertFalse(sut.hasInterVocabularyTermRelationships(subjectVocabulary.getUri(), targetVocabulary.getUri()));
    }

    @Test
    void hasInterVocabularyTermRelationshipsReturnsTrueForSKOSRelatedTermsInSpecifiedVocabularies() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(Collections.singleton(targetVocabulary.getUri()));
        final Term child = Generator.generateTermWithId();
        final Term parentTerm = Generator.generateTermWithId();
        child.addParentTerm(parentTerm);
        subjectVocabulary.getGlossary().addRootTerm(child);
        targetVocabulary.getGlossary().addRootTerm(parentTerm);
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
            child.setGlossary(subjectVocabulary.getGlossary().getUri());
            em.persist(child, descriptorFactory.termDescriptor(subjectVocabulary));
            parentTerm.setGlossary(targetVocabulary.getGlossary().getUri());
            em.persist(parentTerm, descriptorFactory.termDescriptor(targetVocabulary));
            Generator.addTermInVocabularyRelationship(child, subjectVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parentTerm, targetVocabulary.getUri(), em);
        });

        assertTrue(sut.hasInterVocabularyTermRelationships(subjectVocabulary.getUri(), targetVocabulary.getUri()));
    }

    @Test
    void hasInterVocabularyTermRelationshipsReturnsTrueForSKOSRelatedTermsInTransitivelyImportedVocabularies() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary transitiveVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(Collections.singleton(targetVocabulary.getUri()));
        targetVocabulary.setImportedVocabularies(Collections.singleton(transitiveVocabulary.getUri()));
        final Term child = Generator.generateTermWithId();
        final Term parentTerm = Generator.generateTermWithId();
        child.addParentTerm(parentTerm);
        subjectVocabulary.getGlossary().addRootTerm(child);
        transitiveVocabulary.getGlossary().addRootTerm(parentTerm);
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
            em.persist(transitiveVocabulary, descriptorFactory.vocabularyDescriptor(transitiveVocabulary));
            child.setGlossary(subjectVocabulary.getGlossary().getUri());
            em.persist(child, descriptorFactory.termDescriptor(subjectVocabulary));
            parentTerm.setGlossary(transitiveVocabulary.getGlossary().getUri());
            em.persist(parentTerm, descriptorFactory.termDescriptor(transitiveVocabulary));
            Generator.addTermInVocabularyRelationship(child, subjectVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parentTerm, transitiveVocabulary.getUri(), em);
        });

        assertTrue(sut.hasInterVocabularyTermRelationships(subjectVocabulary.getUri(), targetVocabulary.getUri()));
    }

    @Test
    void getTransitivelyImportedVocabulariesReturnsAllImportedVocabulariesForVocabulary() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary importedVocabularyOne = Generator.generateVocabularyWithId();
        final Vocabulary importedVocabularyTwo = Generator.generateVocabularyWithId();
        final Vocabulary transitiveVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(
                new HashSet<>(Arrays.asList(importedVocabularyOne.getUri(), importedVocabularyTwo.getUri())));
        importedVocabularyOne.setImportedVocabularies(Collections.singleton(transitiveVocabulary.getUri()));
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(importedVocabularyOne, descriptorFactory.vocabularyDescriptor(importedVocabularyOne));
            em.persist(importedVocabularyTwo, descriptorFactory.vocabularyDescriptor(importedVocabularyTwo));
            em.persist(transitiveVocabulary, descriptorFactory.vocabularyDescriptor(transitiveVocabulary));
        });

        final Collection<URI> result = sut.getTransitivelyImportedVocabularies(subjectVocabulary);
        assertEquals(3, result.size());
        assertTrue(result.contains(importedVocabularyOne.getUri()));
        assertTrue(result.contains(importedVocabularyTwo.getUri()));
        assertTrue(result.contains(transitiveVocabulary.getUri()));
    }

    @Test
    void initializesLastModificationTimestampToCurrentDateTimeOnInit() {
        final long result = sut.getLastModified();
        assertThat(result, greaterThan(0L));
        assertThat(result, lessThanOrEqualTo(System.currentTimeMillis()));
    }

    @Test
    void refreshLastModifiedUpdatesLastModifiedTimestampToCurrentDateTime() throws Exception {
        final long before = sut.getLastModified();
        Thread.sleep(100);  // force time to move on
        sut.refreshLastModified(new RefreshLastModifiedEvent(this));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void persistRefreshesLastModifiedValue() {
        final long before = sut.getLastModified();
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> sut.persist(voc));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void removeRefreshesLastModifiedValue() {
        final long before = sut.getLastModified();
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));
        transactional(() -> sut.remove(voc));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void updateRefreshesLastModifiedValue() {
        final long before = sut.getLastModified();
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));
        final String newLabel = "New vocabulary label";
        voc.setLabel(newLabel);
        transactional(() -> sut.update(voc));
        final Optional<Vocabulary> result = sut.find(voc.getUri());
        assertTrue(result.isPresent());
        assertEquals(newLabel, result.get().getLabel());
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void findAllByWorkspaceLoadsVocabulariesInWorkspace() {
        enableRdfsInference(em);
        final List<Vocabulary> vocabularies = IntStream.range(0, 10).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        final Workspace workspace = new Workspace();
        workspace.setLabel("test workspace");
        workspace.setUri(Generator.generateUri());
        transactional(() -> {
            vocabularies.forEach(v -> em.persist(v, new EntityDescriptor(v.getUri())));
            em.persist(workspace, new EntityDescriptor(workspace.getUri()));
        });
        final List<Vocabulary> inWorkspace = vocabularies.stream().filter(v -> Generator.randomBoolean())
                                                         .collect(Collectors.toList());
        addWorkspaceReference(inWorkspace, workspace);

        final List<Vocabulary> result = sut.findAll(workspace);
        inWorkspace.sort(Comparator.comparing(Vocabulary::getLabel));
        assertEquals(inWorkspace, result);
    }

    private void addWorkspaceReference(Collection<Vocabulary> vocabularies, Workspace workspace) {
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                conn.begin();
                conn.add(WorkspaceGenerator.generateWorkspaceReferences(vocabularies, workspace));
                conn.commit();
            }
        });
    }

    @Test
    void findLoadsVocabularyInWorkspace() {
        Mockito.reset(workspaceStore);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Workspace workspace = WorkspaceGenerator.generateWorkspace();
        workspace.setUri(WorkspaceTestConfig.DEFAULT_WORKSPACE);
        vocabulary.setUri(WorkspaceTestConfig.DEFAULT_VOCABULARY_CTX);
        transactional(() -> {
            em.persist(vocabulary, new EntityDescriptor(WorkspaceTestConfig.DEFAULT_VOCABULARY_CTX));
            em.persist(workspace, new EntityDescriptor(workspace.getUri()));
            WorkspaceGenerator.generateWorkspaceReferences(Collections.singleton(vocabulary), workspace);
        });
        workspaceStore.setCurrentWorkspace(workspace.getUri());

        final Optional<Vocabulary> result = sut.find(vocabulary.getUri());
        assertTrue(result.isPresent());
        assertEquals(vocabulary, result.get());
    }

    @Test
    void findAllInWorkspaceHandlesDuplicateTitleInDifferentWorkspace() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Workspace workspace = WorkspaceGenerator.generateWorkspace();
        transactional(() -> {
            em.persist(vocabulary, new EntityDescriptor(vocabulary.getUri()));
            em.persist(workspace, new EntityDescriptor(workspace.getUri()));
        });
        addWorkspaceReference(Collections.singleton(vocabulary), workspace);
        saveVocabularyIntoDifferentWorkspace(vocabulary);

        final List<Vocabulary> result = sut.findAll(workspace);
        assertEquals(Collections.singletonList(vocabulary), result);
    }

    private void saveVocabularyIntoDifferentWorkspace(Vocabulary vocabulary) {
        final Vocabulary copy = Generator.generateVocabulary();
        copy.setUri(vocabulary.getUri());
        final Workspace otherWorkspace = WorkspaceGenerator.generateWorkspace();
        transactional(() -> {
            em.persist(copy, new EntityDescriptor(URI.create(copy.getUri().toString() + "-copy")));
            em.persist(otherWorkspace, new EntityDescriptor(otherWorkspace.getUri()));
        });
        addWorkspaceReference(Collections.singleton(copy), otherWorkspace);
    }
}
