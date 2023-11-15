/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Model;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ResourceDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ResourceDao sut;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    private Resource generateResource() {
        final Resource resource = Generator.generateResourceWithId();
        transactional(() -> em.persist(resource));
        return resource;
    }

    @Test
    void findAllDoesNotReturnFilesContainedInDocuments() {
        enableRdfsInference(em);
        final Resource rOne = Generator.generateResourceWithId();
        final Document doc = new Document();
        doc.setUri(Generator.generateUri());
        doc.setLabel("document");
        final File file = new File();
        file.setUri(Generator.generateUri());
        file.setLabel("mpp.html");
        doc.addFile(file);
        transactional(() -> {
            em.persist(rOne);
            em.persist(doc);
            em.persist(file);
        });

        final List<Resource> result = sut.findAll();
        assertEquals(2, result.size());
        assertFalse(result.contains(file));
        assertTrue(result.contains(doc));
        final Optional<Resource> docResult = result.stream().filter(r -> r.getUri().equals(doc.getUri())).findAny();
        assertTrue(docResult.isPresent());
        assertTrue(((Document) docResult.get()).getFile(file.getLabel()).isPresent());
    }

    @Test
    void findAllReturnsResourcesOrderedByLabel() {
        final List<Resource> resources = IntStream.range(0, 10).mapToObj(i -> generateResource())
                                                  .collect(Collectors.toList());

        final List<Resource> result = sut.findAll();
        resources.sort(Comparator.comparing(Resource::getLabel));
        assertEquals(resources, result);
    }

    @Test
    void persistDocumentWithVocabularyPersistsToVocabularyContext() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();

        transactional(() -> sut.persist(doc, vocabulary));
        final Document result = em
                .find(Document.class, doc.getUri(), descriptorFactory.documentDescriptor(vocabulary.getUri()));
        assertNotNull(result);
        assertEquals(doc, result);
    }

    @Test
    void persistFileWithVocabularyPersistToVocabularyContext() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final File file = new File();
        file.setLabel("test.html");
        file.setUri(Generator.generateUri());

        transactional(() -> sut.persist(file, vocabulary));
        final File result = em.find(File.class, file.getUri(), descriptorFactory.fileDescriptor(vocabulary.getUri()));
        assertNotNull(result);
        assertEquals(file, result);
    }

    @Test
    void persistWithVocabularyThrowsIllegalArgumentForGenericResource() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Resource resource = Generator.generateResourceWithId();
        assertThrows(IllegalArgumentException.class, () -> sut.persist(resource, vocabulary));
    }

    @Test
    void updateDocumentWithRelatedVocabularyUpdatesDocumentInVocabularyContext() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        vocabulary.setDocument(doc);

        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(doc, descriptorFactory.documentDescriptor(vocabulary));
        });
        doc.setVocabulary(vocabulary.getUri());

        final String newLabel = "new label";
        doc.setLabel(newLabel);

        transactional(() -> sut.update(doc));
        final Document result = em
                .find(Document.class, doc.getUri(), descriptorFactory.documentDescriptor(vocabulary.getUri()));
        assertNotNull(result);
        assertEquals(newLabel, result.getLabel());
    }

    @Test
    void updateFileInDocumentWithRelatedVocabularyUpdatesFileInVocabularyContext() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        vocabulary.setDocument(doc);
        final File file = Generator.generateFileWithId("test.html");
        doc.addFile(file);
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(doc, descriptorFactory.documentDescriptor(vocabulary));
            em.persist(file, descriptorFactory.fileDescriptor(vocabulary));
        });
        file.setDocument(doc);
        doc.setVocabulary(vocabulary.getUri());

        final String newLabel = "new-test.html";
        file.setLabel(newLabel);

        transactional(() -> sut.update(file));
        final File result = em.find(File.class, file.getUri(), descriptorFactory.fileDescriptor(vocabulary));
        assertNotNull(result);
        assertEquals(newLabel, result.getLabel());
    }

    @Test
    void updateEvictsCachedVocabularyToPreventIssuesWithStaleReferencesBetweenContexts() {
        final Vocabulary vocabulary = new Vocabulary();
        vocabulary.setUri(Generator.generateUri());
        vocabulary.setLabel("vocabulary");
        vocabulary.setGlossary(new Glossary());
        vocabulary.setModel(new Model());
        final Document document = Generator.generateDocumentWithId();
        vocabulary.setDocument(document);
        document.setVocabulary(vocabulary.getUri());
        final File file = new File();
        file.setLabel("test.html");
        file.setUri(Generator.generateUri());
        file.setDocument(document);
        document.addFile(file);
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(document, descriptorFactory.documentDescriptor(vocabulary));
            em.persist(file, descriptorFactory.fileDescriptor(vocabulary));
        });

        transactional(() -> {
            final File toRemove = em.getReference(File.class, file.getUri());
            sut.remove(toRemove);
            file.getDocument().removeFile(file);
            sut.update(file.getDocument());
        });

        transactional(() -> {
            final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri(),
                                              descriptorFactory.vocabularyDescriptor(vocabulary));
            assertThat(result.getDocument().getFiles(), anyOf(nullValue(), empty()));
        });
    }

    @Test
    void detachDetachesInstanceFromPersistenceContext() {
        final Resource resource = Generator.generateResourceWithId();
        transactional(() -> em.persist(resource));

        transactional(() -> {
            final Resource toDetach = sut.find(resource.getUri()).get();
            assertTrue(sut.em.contains(toDetach));
            sut.detach(toDetach);
            assertFalse(sut.em.contains(toDetach));
        });
    }

    @Test
    void detachDoesNothingForNonManagedInstance() {
        final Resource resource = Generator.generateResourceWithId();

        transactional(() -> {
            assertFalse(sut.em.contains(resource));
            sut.detach(resource);
            assertFalse(sut.em.contains(resource));
        });
    }

    /**
     * Bug #1000
     */
    @Test
    void updateVocabularyDocumentWorksCorrectlyWithContexts() {
        enableRdfsInference(em);
        final Document doc = Generator.generateDocumentWithId();
        final Vocabulary voc = new Vocabulary();
        voc.setUri(Generator.generateUri());
        voc.setLabel("Test vocabulary");
        voc.setDocument(doc);
        voc.setGlossary(new Glossary());
        voc.setModel(new Model());

        transactional(() -> {
            em.persist(voc, descriptorFactory.vocabularyDescriptor(voc));
            em.persist(doc, descriptorFactory.documentDescriptor(voc));
        });
        doc.setVocabulary(voc.getUri());

        final File f = Generator.generateFileWithId("test.html");

        transactional(() -> {
            doc.addFile(f);
            sut.persist(f, voc);
            sut.update(doc);
        });

        assertNotNull(em.find(File.class, f.getUri(), descriptorFactory.fileDescriptor(voc)));
        final Document result = em.find(Document.class, doc.getUri());
        assertThat(result.getFiles(), hasItem(f));
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
        final Resource resource = Generator.generateResourceWithId();
        transactional(() -> sut.persist(resource));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void removeRefreshesLastModifiedValue() {
        final long before = sut.getLastModified();
        final Resource resource = generateResource();
        transactional(() -> sut.remove(resource));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void updateRefreshesLastModifiedValue() {
        final Resource resource = generateResource();
        final long before = sut.getLastModified();
        final String newLabel = "New label";
        resource.setLabel(newLabel);
        transactional(() -> sut.update(resource));
        final Optional<Resource> result = sut.find(resource.getUri());
        assertTrue(result.isPresent());
        assertEquals(newLabel, result.get().getLabel());
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void removeFileUpdatesParentDocumentInVocabularyContext() {
        final Document document = Generator.generateDocumentWithId();
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = new cz.cvut.kbss.termit.model.Vocabulary();
        vocabulary.setUri(Generator.generateUri());
        vocabulary.setLabel("Vocabulary");
        vocabulary.setGlossary(new Glossary());
        vocabulary.setModel(new Model());
        vocabulary.setDocument(document);
        document.setVocabulary(vocabulary.getUri());
        final File file = new File();
        file.setLabel("test.html");
        file.setUri(Generator.generateUri());
        file.setDocument(document);
        final File fileTwo = new File();
        fileTwo.setLabel("test-two.html");
        fileTwo.setUri(Generator.generateUri());
        fileTwo.setDocument(document);
        document.addFile(fileTwo);
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(document, descriptorFactory.documentDescriptor(vocabulary));
            em.persist(file, descriptorFactory.fileDescriptor(vocabulary));
            em.persist(fileTwo, descriptorFactory.fileDescriptor(vocabulary));
        });

        transactional(() -> {
            final Resource toRemove = sut.getReference(file.getUri()).get();
            sut.remove(toRemove);
        });

        final cz.cvut.kbss.termit.model.Vocabulary
                result = em.find(cz.cvut.kbss.termit.model.Vocabulary.class, vocabulary.getUri(),
                                 descriptorFactory.vocabularyDescriptor(vocabulary));
        assertEquals(1, result.getDocument().getFiles().size());
        assertTrue(result.getDocument().getFiles().contains(fileTwo));
    }

    @Test
    void updateSupportsSubclassesOfResource() {
        final Document doc = Generator.generateDocumentWithId();
        final File fileOne = Generator.generateFileWithId("test.html");
        doc.addFile(fileOne);
        final File fileTwo = Generator.generateFileWithId("testTwo.html");
        transactional(() -> {
            // Ensure correct RDFS class hierarchy interpretation
            final Repository repository = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repository.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                conn.add(vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_c_dokument), RDFS.SUBCLASSOF, vf.createIRI(
                        cz.cvut.kbss.termit.util.Vocabulary.s_c_zdroj));
            }
            em.persist(doc);
            em.persist(fileOne);
            em.persist(fileTwo);
        });

        final String newName = "Updated name";
        doc.setLabel(newName);
        final String newDescription = "Document description.";
        doc.setDescription(newDescription);
        doc.addFile(fileTwo);
        transactional(() -> sut.update(doc));
        final Document result = em.find(Document.class, doc.getUri());
        assertEquals(newName, result.getLabel());
        assertEquals(newDescription, result.getDescription());
        assertEquals(2, result.getFiles().size());
        assertTrue(result.getFiles().contains(fileTwo));
    }
}
