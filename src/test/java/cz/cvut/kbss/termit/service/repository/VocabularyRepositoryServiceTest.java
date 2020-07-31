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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.workspace.WorkspaceMetadataCache;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

class VocabularyRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private Configuration config;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private EntityManager em;

    @Autowired
    private WorkspaceMetadataCache workspaceMetadataCache;

    @Autowired
    private VocabularyRepositoryService sut;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    @Test
    void persistGeneratesPersistChangeRecord() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        sut.persist(vocabulary);

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);

        final PersistChangeRecord record = em
                .createQuery("SELECT r FROM PersistChangeRecord r WHERE r.changedEntity = :vocabularyIri",
                        PersistChangeRecord.class).setParameter("vocabularyIri", vocabulary.getUri()).getSingleResult();
        assertNotNull(record);
        assertEquals(user.toUser(), record.getAuthor());
        assertNotNull(record.getTimestamp());
    }

    @Test
    void persistThrowsValidationExceptionWhenVocabularyNameIsBlank() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setLabel("");
        final ValidationException exception = assertThrows(ValidationException.class, () -> sut.persist(vocabulary));
        assertThat(exception.getMessage(), containsString("label must not be blank"));
    }

    @Test
    void persistGeneratesIdentifierWhenInstanceDoesNotHaveIt() {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        sut.persist(vocabulary);
        assertNotNull(vocabulary.getUri());

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);
        assertThat(result.getUri().toString(), containsString(IdentifierResolver.normalize(vocabulary.getLabel())));
    }

    @Test
    void persistDoesNotGenerateIdentifierWhenInstanceAlreadyHasOne() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final URI originalUri = vocabulary.getUri();
        sut.persist(vocabulary);
        assertNotNull(vocabulary.getUri());

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);
        assertEquals(originalUri, result.getUri());
    }

    @Test
    void persistCreatesGlossaryAndModelInstances() {
        final Vocabulary vocabulary = new Vocabulary();
        vocabulary.setUri(Generator.generateUri());
        vocabulary.setLabel("TestVocabulary");
        sut.persist(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result.getGlossary());
        assertNotNull(result.getModel());
    }

    @Test
    void persistThrowsResourceExistsExceptionWhenAnotherVocabularyWithIdenticalIdentifierAlreadyIriExists() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary));

        final Vocabulary toPersist = Generator.generateVocabulary();
        toPersist.setUri(vocabulary.getUri());
        assertThrows(ResourceExistsException.class, () -> sut.persist(toPersist));
    }

    @Test
    void updateThrowsValidationExceptionForEmptyName() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary));

        vocabulary.setLabel("");
        assertThrows(ValidationException.class, () -> sut.update(vocabulary));
    }

    private Descriptor descriptorFor(Vocabulary entity) {
        return descriptorFactory.vocabularyDescriptor(entity);
    }

    @Test
    void updateSavesUpdatedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));

        final String newName = "Updated name";
        vocabulary.setLabel(newName);
        sut.update(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);
        assertEquals(newName, result.getLabel());
    }

    @Test
    void removeRemovesNondocumentEmptyNonImportedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));
        sut.remove(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNull(result);
    }

    @Test
    void generateIdentifierGeneratesIdentifierBasedOnSpecifiedLabel() {
        final String label = "Test vocabulary";
        assertEquals(config.get(ConfigParam.NAMESPACE_VOCABULARY) + IdentifierResolver.normalize(label),
                sut.generateIdentifier(label).toString());
    }

    @Test
    void updateThrowsVocabularyImportExceptionWhenTryingToDeleteVocabularyImportRelationshipAndTermsAreStillRelated() {
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

        subjectVocabulary.setImportedVocabularies(Collections.emptySet());
        assertThrows(VocabularyImportException.class, () -> sut.update(subjectVocabulary));
    }

    @Test
    void updateUpdatesVocabularyWithImportRemovalWhenNoRelationshipsBetweenTermsExist() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(Collections.singleton(targetVocabulary.getUri()));
        final Term child = Generator.generateTermWithId();
        final Term parentTerm = Generator.generateTermWithId();
        subjectVocabulary.getGlossary().addRootTerm(child);
        child.setVocabulary(subjectVocabulary.getUri());
        targetVocabulary.getGlossary().addRootTerm(parentTerm);
        parentTerm.setVocabulary(targetVocabulary.getUri());
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

        subjectVocabulary.setImportedVocabularies(Collections.emptySet());
        sut.update(subjectVocabulary);
        assertThat(em.find(Vocabulary.class, subjectVocabulary.getUri()).getImportedVocabularies(),
                anyOf(nullValue(), IsEmptyCollection.empty()));
    }

    @Test
    void getTransitivelyImportedVocabulariesReturnsEmptyCollectionsWhenVocabularyHasNoImports() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary)));
        final Collection<URI> result = sut.getTransitivelyImportedVocabularies(subjectVocabulary);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getLastModifiedReturnsInitializedValue() {
        final long result = sut.getLastModified();
        assertThat(result, greaterThan(0L));
        assertThat(result, lessThanOrEqualTo(System.currentTimeMillis()));
    }

    @Test
    void getChangesRetrievesChangesForVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final List<AbstractChangeRecord> changes = sut.getChanges(vocabulary);
        assertTrue(changes.isEmpty());
    }

    @Test
    void findAllRetrievesVocabulariesFromCurrentWorkspace() {
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
        setCurrentWorkspace(workspace, inWorkspace);

        final List<Vocabulary> result = sut.findAll();
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

    private void setCurrentWorkspace(Workspace workspace, Collection<Vocabulary> vocabularies) {
        final WorkspaceMetadata metadata = workspaceMetadataCache.getCurrentWorkspaceMetadata();
        vocabularies.forEach(v -> doReturn(new VocabularyInfo(v.getUri(), v.getUri(), v.getUri())).when(metadata)
                                                                                                  .getVocabularyInfo(
                                                                                                          v.getUri()));
        doReturn(workspace).when(workspaceMetadataCache).getCurrentWorkspace();
    }
}
