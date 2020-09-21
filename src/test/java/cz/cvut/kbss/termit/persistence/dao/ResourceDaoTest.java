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
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.assignment.*;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.selector.XPathSelector;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private ResourceDao sut;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    @Test
    void findTermsReturnsTermsWhichAreAssignedToSpecifiedResource() {
        final Resource resource = generateResource();
        final List<Term> terms = generateTerms(resource);

        final List<Term> result = sut.findTerms(resource);
        assertEquals(terms.size(), result.size());
        assertTrue(terms.containsAll(result));
    }

    private Resource generateResource() {
        final Resource resource = Generator.generateResourceWithId();
        transactional(() -> em.persist(resource));
        return resource;
    }

    private List<Term> generateTerms(Resource resource) {
        final List<Term> terms = new ArrayList<>();
        final List<Term> matching = new ArrayList<>();
        final List<TermAssignment> assignments = new ArrayList<>();
        final Target target = new Target(resource);
        for (int i = 0; i < Generator.randomInt(2, 10); i++) {
            final Term t = Generator.generateTermWithId();
            terms.add(t);
            if (Generator.randomBoolean() || matching.isEmpty()) {
                matching.add(t);
                final TermAssignment ta = new TermAssignment();
                ta.setTerm(t.getUri());
                ta.setTarget(target);
                assignments.add(ta);
            }
        }
        transactional(() -> {
            terms.forEach(em::persist);
            assignments.forEach(ta -> {
                em.persist(target);
                em.persist(ta);
            });
        });
        return matching;
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
    void findTermsReturnsDistinctTermsInCaseSomeOccurMultipleTimesInResource() {
        final File resource = new File();
        resource.setLabel("test.html");
        resource.setUri(Generator.generateUri());
        transactional(() -> em.persist(resource));
        final List<Term> terms = generateTerms(resource);
        generateOccurrences(resource, terms);
        final List<Term> result = sut.findTerms(resource);
        final Set<Term> resultSet = new HashSet<>(result);
        assertEquals(result.size(), resultSet.size());
    }

    private void generateOccurrences(File resource, List<Term> terms) {
        final List<TermOccurrence> occurrences = new ArrayList<>();
        for (Term t : terms) {
            final TermOccurrence occurrence = new TermFileOccurrence(t.getUri(), new FileOccurrenceTarget(resource));
            // Dummy selector
            occurrence.getTarget().setSelectors(Collections.singleton(new XPathSelector("//div")));
            occurrences.add(occurrence);
        }
        transactional(() -> occurrences.forEach(occ -> {
            em.persist(occ);
            em.persist(occ.getTarget());
        }));
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
                .find(Document.class, doc.getUri(), DescriptorFactory.documentDescriptor(vocabulary.getUri()));
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
        final File result = em.find(File.class, file.getUri(), DescriptorFactory.fileDescriptor(vocabulary.getUri()));
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
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();

        transactional(() -> {
            em.persist(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(doc, DescriptorFactory.documentDescriptor(vocabulary));
        });
        insertInferredDocumentVocabularyPropertyAssertions(doc, vocabulary);
        doc.setVocabulary(vocabulary.getUri());

        final String newLabel = "new label";
        doc.setLabel(newLabel);

        transactional(() -> sut.update(doc));
        final Document result = em
                .find(Document.class, doc.getUri(), DescriptorFactory.documentDescriptor(vocabulary.getUri()));
        assertNotNull(result);
        assertEquals(newLabel, result.getLabel());
    }

    private void insertInferredDocumentVocabularyPropertyAssertions(Document document, Vocabulary vocabulary) {
        transactional(() -> {
            final Repository repository = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repository.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                conn.add(vf.createIRI(document.getUri().toString()),
                        vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_dokumentovy_slovnik),
                        vf.createIRI(vocabulary.getUri().toString()),
                        vf.createIRI(DescriptorFactory.vocabularyDescriptor(vocabulary).getContext().toString()));
                if (document.getFiles() != null) {
                    document.getFiles().forEach(f -> conn.add(
                            vf.createIRI(f.getUri().toString()),
                            vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_casti_dokumentu),
                            vf.createIRI(document.getUri().toString()),
                            vf.createIRI(DescriptorFactory.vocabularyDescriptor(vocabulary).getContext().toString())
                    ));
                }
            }
        });
    }

    @Test
    void updateFileInDocumentWithRelatedVocabularyUpdatesFileInVocabularyContext() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        final File file = Generator.generateFileWithId("test.html");
        doc.addFile(file);
        transactional(() -> {
            em.persist(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(doc, DescriptorFactory.documentDescriptor(vocabulary));
            em.persist(file, DescriptorFactory.fileDescriptor(vocabulary));
        });
        insertInferredDocumentVocabularyPropertyAssertions(doc, vocabulary);
        file.setDocument(doc);
        doc.setVocabulary(vocabulary.getUri());

        final String newLabel = "new-test.html";
        file.setLabel(newLabel);

        transactional(() -> sut.update(file));
        final File result = em.find(File.class, file.getUri(), DescriptorFactory.fileDescriptor(vocabulary));
        assertNotNull(result);
        assertEquals(newLabel, result.getLabel());
    }

    @Test
    void updateEvictsCachedVocabularyToPreventIssuesWithStaleReferencesBetweenContexts() {
        final DocumentVocabulary vocabulary = new DocumentVocabulary();
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
            em.persist(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(document, DescriptorFactory.documentDescriptor(vocabulary));
            em.persist(file, DescriptorFactory.fileDescriptor(vocabulary));
        });

        transactional(() -> {
            final File toRemove = em.getReference(File.class, file.getUri());
            sut.remove(toRemove);
            file.getDocument().removeFile(file);
            sut.update(file.getDocument());
        });

        transactional(() -> {
            final DocumentVocabulary result = em.find(DocumentVocabulary.class, vocabulary.getUri(),
                    DescriptorFactory.vocabularyDescriptor(vocabulary));
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
        final Document doc = Generator.generateDocumentWithId();
        final DocumentVocabulary voc = new DocumentVocabulary();
        voc.setUri(Generator.generateUri());
        voc.setLabel("Test vocabulary");
        voc.setDocument(doc);
        voc.setGlossary(new Glossary());
        voc.setModel(new Model());

        transactional(() -> em.persist(voc, DescriptorFactory.vocabularyDescriptor(voc)));
        insertInferredDocumentVocabularyPropertyAssertions(doc, voc);
        doc.setVocabulary(voc.getUri());

        final File f = Generator.generateFileWithId("test.html");

        transactional(() -> {
            doc.addFile(f);
            sut.persist(f, voc);
            sut.update(doc);
        });

        assertNotNull(em.find(File.class, f.getUri(), DescriptorFactory.fileDescriptor(voc)));
        assertTrue(
                em.find(Document.class, doc.getUri(), DescriptorFactory.documentDescriptor(voc)).getFile(f.getLabel())
                  .isPresent());
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
    void updateRefreshesLastModifiedValue() throws Exception {
        final Resource resource = generateResource();
        final long before = sut.getLastModified();
        final String newLabel = "New label";
        resource.setLabel(newLabel);
        Thread.sleep(100);  // force time to move on
        transactional(() -> sut.update(resource));
        final Optional<Resource> result = sut.find(resource.getUri());
        assertTrue(result.isPresent());
        assertEquals(newLabel, result.get().getLabel());
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }
}
