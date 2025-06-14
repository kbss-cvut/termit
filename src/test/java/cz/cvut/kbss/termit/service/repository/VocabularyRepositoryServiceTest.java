/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.SnapshotNotEditableException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static cz.cvut.kbss.termit.environment.Environment.getPrimaryLabel;
import static cz.cvut.kbss.termit.environment.Environment.setPrimaryLabel;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private Configuration config;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private EntityManager em;

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
                             PersistChangeRecord.class).setParameter("vocabularyIri", vocabulary.getUri())
                .getSingleResult();
        assertNotNull(record);
        assertEquals(user.toUser(), record.getAuthor());
        assertNotNull(record.getTimestamp());
    }

    @Test
    void persistThrowsValidationExceptionWhenVocabularyNameIsBlank() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        setPrimaryLabel(vocabulary, "");
        final ValidationException exception = assertThrows(ValidationException.class, () -> sut.persist(vocabulary));
        assertThat(exception.getMessage(),
                   containsString("label in the primary configured language must not be blank"));
    }

    @Test
    void persistGeneratesIdentifierWhenInstanceDoesNotHaveIt() {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        sut.persist(vocabulary);
        assertNotNull(vocabulary.getUri());

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);
        assertThat(result.getUri().toString(),
                   containsString(IdentifierResolver.normalize(getPrimaryLabel(vocabulary))));
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
        setPrimaryLabel(vocabulary, "TestVocabulary");
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
    void persistGeneratesPreferredNamespaceWithVocabularyIdentifierAndTermSeparatorAsValue() {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        sut.persist(vocabulary);
        assertNotNull(vocabulary.getUri());

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);
        assertThat(result.getProperties().keySet(),
                   hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
        assertEquals(Set.of(result.getUri() + config.getNamespace().getTerm().getSeparator()),
                     result.getProperties().get(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
    }

    @Test
    void updateThrowsValidationExceptionForEmptyName() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));

        setPrimaryLabel(vocabulary, "");
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
        vocabulary.setLabel(MultilingualString.create(newName, Environment.LANGUAGE));
        sut.update(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);
        assertEquals(newName, result.getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void removeRemovesEmptyVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));
        sut.remove(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNull(result);
    }

    @Test
    void removeThrowsWhenVocabularyIsImported() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary importing = Generator.generateVocabularyWithId();
        importing.setImportedVocabularies(Set.of(vocabulary.getUri()));
        transactional(() -> {
            em.persist(vocabulary, descriptorFor(vocabulary));
            em.persist(importing, descriptorFor(importing));
        });

        assertThrows(AssetRemovalException.class, () -> sut.remove(vocabulary));

        // ensure nothing was deleted
        final Vocabulary v = em.find(Vocabulary.class, vocabulary.getUri());
        final Vocabulary i = em.find(Vocabulary.class, importing.getUri());

        assertNotNull(v);
        assertNotNull(i);
    }

    /**
     * @see cz.cvut.kbss.termit.util.Constants#SKOS_CONCEPT_MATCH_RELATIONSHIPS
     */
    @ParameterizedTest
    @MethodSource("cz.cvut.kbss.termit.persistence.dao.VocabularyDaoTest#skosConceptMatchRelationshipsSource")
    void removeThrowsWhenTermRelationExists(URI relation) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary secondVocabulary = Generator.generateVocabularyWithId();

        final Term objectTerm = Generator.generateTermWithId(vocabulary.getUri());
        final Term subjectTerm = Generator.generateTermWithId(secondVocabulary.getUri());

        transactional(() -> {
            em.persist(vocabulary, descriptorFor(vocabulary));
            em.persist(secondVocabulary, descriptorFor(secondVocabulary));
            em.persist(objectTerm, descriptorFactory.termDescriptor(objectTerm));
            em.persist(subjectTerm, descriptorFactory.termDescriptor(subjectTerm));

            Environment.addRelation(subjectTerm.getUri(), relation, objectTerm.getUri(), em);
        });

        assertThrows(AssetRemovalException.class, () -> sut.remove(vocabulary));
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
    void importVocabularyImportsAValidVocabulary() {
        final String skos =
                "@prefix skos : <http://www.w3.org/2004/02/skos/core#> . " +
                        "@prefix dc : <http://purl.org/dc/terms/> . " +
                        "<https://example.org/cs> a skos:ConceptScheme ; dc:title \"Test\"@en . " +
                        "<https://example.org/pojem/a> a skos:Concept ; skos:inScheme <https://example.org/cs> . ";


        final MultipartFile mf = new MockMultipartFile(
                "file",
                "thesaurus",
                "text/turtle",
                skos.getBytes(StandardCharsets.UTF_8)
        );

        final Vocabulary v = sut.importVocabulary(true, mf);
        assertEquals("Test", getPrimaryLabel(v));
    }

    @Test
    void importVocabularyCorrectlyImportsTurtleFileOnWindows() {
        final String skos =
                "@prefix skos : <http://www.w3.org/2004/02/skos/core#> . " +
                        "@prefix dc : <http://purl.org/dc/terms/> . " +
                        "<https://example.org/cs> a skos:ConceptScheme ; dc:title \"Test\"@en . " +
                        "<https://example.org/pojem/a> a skos:Concept ; skos:inScheme <https://example.org/cs> . ";


        final MultipartFile mf = new MockMultipartFile(
                "file",
                "thesaurus",
                "application/octet-stream",
                skos.getBytes(StandardCharsets.UTF_8)
        );

        final Vocabulary v = sut.importVocabulary(true, mf);
        assertEquals("Test", getPrimaryLabel(v));
    }

    @Test
    void importVocabularyThrowsExceptionOnMissingConceptScheme() {
        final String skos =
                "@prefix skos : <http://www.w3.org/2004/02/skos/core#> . " +
                        "@prefix dc : <http://purl.org/dc/terms/> . " +
                        "<https://example.org/pojem/a> a skos:Concept ; skos:inScheme <https://example.org/cs> . ";

        Assertions.assertThrows(TermItException.class, () -> {
            final MultipartFile mf = new MockMultipartFile(
                    "test",
                    "test",
                    "text/turtle",
                    skos.getBytes(StandardCharsets.UTF_8)
            );
            final Vocabulary v = sut.importVocabulary(false, mf);
            assertEquals("Test", getPrimaryLabel(v));
        });
    }

    @Test
    void getTermCountRetrievesNumberOfTermsInVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        assertEquals(1, sut.getTermCount(vocabulary));
    }

    @Test
    void persistGeneratesGlossaryIriBasedOnVocabularyIriAndConfiguredFragment() {
        final String label = "Test vocabulary " + System.currentTimeMillis();
        final Vocabulary vocabulary = new Vocabulary();
        setPrimaryLabel(vocabulary, label);
        sut.persist(vocabulary);
        assertNotNull(vocabulary.getUri());
        assertNotNull(vocabulary.getGlossary());

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertEquals(vocabulary.getUri() + "/" + config.getGlossary().getFragment(),
                     result.getGlossary().getUri().toString());
    }

    @Test
    void persistGeneratesModelIriBasedOnVocabularyIri() {
        final String label = "Test vocabulary " + System.currentTimeMillis();
        final Vocabulary vocabulary = new Vocabulary();
        setPrimaryLabel(vocabulary, label);
        sut.persist(vocabulary);
        assertNotNull(vocabulary.getUri());
        assertNotNull(vocabulary.getModel());

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertEquals(vocabulary.getUri() + "/" + Constants.DEFAULT_MODEL_IRI_COMPONENT,
                     result.getModel().getUri().toString());
    }

    @Test
    void findVersionValidAtThrowsNotFoundExceptionWhenNoValidSnapshotExists() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));

        final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.DAYS);
        assertThrows(NotFoundException.class, () -> sut.findVersionValidAt(vocabulary, timestamp));
    }

    @Test
    void updateOfSnapshotThrowsSnapshotNotEditableException() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));

        setPrimaryLabel(vocabulary, "Updated label");
        assertThrows(SnapshotNotEditableException.class, () -> sut.update(vocabulary));
    }

    @Test
    void updateEnsuresReferenceToAccessControlListIsPreserved() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setAcl(Generator.generateUri());
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));

        final Vocabulary update = new Vocabulary(vocabulary.getUri());
        update.setModel(vocabulary.getModel());
        update.setGlossary(vocabulary.getGlossary());
        setPrimaryLabel(update, "Updated label");
        // Intentionally leave ACL null, this is how it would arrive from the client

        transactional(() -> sut.update(update));

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertEquals(update.getLabel(), result.getLabel());
        assertEquals(vocabulary.getAcl(), result.getAcl());
    }

    @Test
    void importCreatesDocumentAssociatedWithVocabulary() {
        final String skos =
                "@prefix skos : <http://www.w3.org/2004/02/skos/core#> . " +
                        "@prefix dc : <http://purl.org/dc/terms/> . " +
                        "<https://example.org/cs> a skos:ConceptScheme ; dc:title \"Test\"@en . " +
                        "<https://example.org/pojem/a> a skos:Concept ; skos:inScheme <https://example.org/cs> . ";


        final MultipartFile mf = new MockMultipartFile(
                "file",
                "thesaurus",
                "text/turtle",
                skos.getBytes(StandardCharsets.UTF_8)
        );

        final Vocabulary v = sut.importVocabulary(true, mf);
        assertNotNull(v.getDocument());
        assertNotNull(em.find(Document.class, v.getDocument().getUri()));
        assertEquals("Document for " + v.getLabel().get(Constants.DEFAULT_LANGUAGE), v.getDocument().getLabel());
    }

    @Test
    void updateSavesLabelInNewLanguage() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));
        final MultilingualString expected = new MultilingualString(vocabulary.getLabel().getValue());

        final String newCsName = "Nový název";
        expected.set("cs", newCsName);
        vocabulary.getLabel().set("cs", newCsName);
        sut.update(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
        assertNotNull(result);
        assertEquals(expected, result.getLabel());
    }
}
