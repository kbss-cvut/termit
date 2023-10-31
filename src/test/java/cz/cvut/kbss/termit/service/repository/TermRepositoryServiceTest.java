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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static cz.cvut.kbss.termit.environment.Generator.generateTermWithId;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private TermRepositoryService sut;

    private UserAccount user;
    private Vocabulary vocabulary;
    private Vocabulary childVocabulary;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);

        this.vocabulary = Generator.generateVocabularyWithId();
        this.childVocabulary = Generator.generateVocabularyWithId();
        childVocabulary.setImportedVocabularies(Collections.singleton(vocabulary.getUri()));
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(childVocabulary, descriptorFactory.vocabularyDescriptor(childVocabulary));
        });
    }

    @Test
    void persistThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> sut.persist(Generator.generateTerm()));
    }

    @Test
    void addTermToVocabularySavesTermAsRoot() {
        final Term term = Generator.generateTermWithId();

        transactional(() -> sut.addRootTermToVocabulary(term, vocabulary));

        transactional(() -> {
            // Need to put in transaction, otherwise EM delegate is closed after find and lazy loading of glossary terms does not work
            final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri());
            assertNotNull(result);
            assertTrue(result.getGlossary().getRootTerms().contains(term.getUri()));
        });
    }

    @Test
    void addTermToVocabularyGeneratesTermIdentifierWhenItIsNotSet() {
        final Term term = Generator.generateTerm();
        transactional(() -> sut.addRootTermToVocabulary(term, vocabulary));

        assertNotNull(term.getUri());
        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
    }

    @Test
    void addTermToVocabularyAddsTermIntoVocabularyContext() {
        final Term term = Generator.generateTermWithId();

        transactional(() -> sut.addRootTermToVocabulary(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertEquals(term, result);
    }

    @Test
    void addTermToVocabularyThrowsValidationExceptionWhenTermNameIsBlank() {
        final Term term = Generator.generateTerm();
        term.setUri(Generator.generateUri());
        term.getLabel().remove(Environment.LANGUAGE);

        final ValidationException exception =
                assertThrows(
                        ValidationException.class, () -> sut.addRootTermToVocabulary(term, vocabulary));
        assertThat(exception.getMessage(), containsString("label must not be blank"));
    }

    @Test
    void addTermToVocabularyThrowsResourceExistsExceptionWhenAnotherTermWithIdenticalAlreadyIriExists() {
        final Term term1 = Generator.generateTerm();
        URI uri = Generator.generateUri();
        term1.setUri(uri);
        sut.addRootTermToVocabulary(term1, vocabulary);

        final Term term2 = Generator.generateTerm();
        term2.setUri(uri);
        assertThrows(
                ResourceExistsException.class, () -> sut.addRootTermToVocabulary(term2, vocabulary));
    }

    @Test
    void addRootTermDoesNotRewriteExistingTermsInGlossary() {
        final Term existing = Generator.generateTermWithId();
        final Term newOne = Generator.generateTermWithId();
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(existing);
            em.persist(existing, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        // Simulate lazily loaded detached root terms
        vocabulary.getGlossary().setRootTerms(null);

        transactional(() -> sut.addRootTermToVocabulary(newOne, vocabulary));

        // Run in transaction to allow lazy fetch of root terms
        transactional(() -> {
            final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri());
            assertNotNull(result);
            assertTrue(result.getRootTerms().contains(existing.getUri()));
            assertTrue(result.getRootTerms().contains(newOne.getUri()));
        });
    }

    @Test
    void addChildTermCreatesSubTermForSpecificTerm() {
        final Term parent = Generator.generateTermWithId();
        // This is normally inferred
        parent.setVocabulary(vocabulary.getUri());
        parent.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        transactional(() -> sut.addChildTerm(child, parent));

        Term result = em.find(Term.class, child.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getParentTerms());
    }

    @Test
    void addChildTermGeneratesIdentifierWhenItIsNotSet() {
        final Term parent = Generator.generateTermWithId();
        final Term child = Generator.generateTerm();
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            parent.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary.getUri()));
            em.merge(vocabulary);
        });

        // This is normally inferred
        parent.setVocabulary(vocabulary.getUri());
        sut.addChildTerm(child, parent);
        assertNotNull(child.getUri());
        final Term result = em.find(Term.class, child.getUri());
        assertNotNull(result);
    }

    @Test
    void addChildTermDoesNotAddTermDirectlyIntoGlossary() {
        final Term parent = Generator.generateTermWithId();
        final Term child = Generator.generateTermWithId();
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            parent.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary());
        });

        // This is normally inferred
        parent.setVocabulary(vocabulary.getUri());
        sut.addChildTerm(child, parent);
        em.getEntityManagerFactory().getCache().evictAll();
        final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri());
        assertEquals(1, result.getRootTerms().size());
        assertTrue(result.getRootTerms().contains(parent.getUri()));
        assertFalse(result.getRootTerms().contains(child.getUri()));
        assertNotNull(em.find(Term.class, child.getUri()));
    }

    @Test
    void addChildTermPersistsTermIntoVocabularyContext() {
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        // This is normally inferred
        parent.setVocabulary(vocabulary.getUri());
        final Term child = Generator.generateTermWithId();
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary.getUri()));
            em.merge(vocabulary.getGlossary());
        });

        transactional(() -> sut.addChildTerm(child, parent));
        final Term result = em.find(Term.class, child.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertEquals(child, result);
    }

    @Test
    void addChildThrowsResourceExistsExceptionWhenTermWithIdenticalIdentifierAlreadyExists() {
        final Term existing = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        final Term child = Generator.generateTerm();
        child.setUri(existing.getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.persist(existing);
            em.persist(parent);
            em.merge(vocabulary.getGlossary());
        });

        assertThrows(ResourceExistsException.class, () -> sut.addChildTerm(child, parent));
    }

    @Test
    void updateUpdatesTermWithParent() {
        final Term t = Generator.generateTermWithId();
        vocabulary.getGlossary().addRootTerm(t);
        t.setVocabulary(vocabulary.getUri());
        final Term childOne = Generator.generateTermWithId();
        childOne.addParentTerm(t);
        childOne.setGlossary(vocabulary.getGlossary().getUri());
        final Term termTwo = Generator.generateTermWithId();
        vocabulary.getGlossary().addRootTerm(termTwo);
        termTwo.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(childOne, descriptorFactory.termDescriptor(vocabulary));
            em.persist(t, descriptorFactory.termDescriptor(vocabulary));
            em.persist(termTwo, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(childOne, vocabulary.getUri(), em);
        });

        childOne.setParentTerms(Collections.singleton(termTwo));
        final String newLabel = "new term label";
        childOne.getLabel().set(Environment.LANGUAGE, newLabel);
        // This is normally inferred
        childOne.setVocabulary(vocabulary.getUri());
        transactional(() -> sut.update(childOne));
        final Term result = em.find(Term.class, childOne.getUri());
        assertEquals(newLabel, result.getLabel().get(Environment.LANGUAGE));
        assertEquals(Collections.singleton(termTwo), result.getParentTerms());
    }

    @Test
    void updateThrowsValidationExceptionForEmptyTermLabel() {
        final Term t = Generator.generateTermWithId();
        vocabulary.getGlossary().addRootTerm(t);
        vocabulary.getGlossary().addRootTerm(t);
        transactional(() -> {
            em.persist(t);
            em.merge(vocabulary);
        });

        t.getLabel().remove(Environment.LANGUAGE);
        assertThrows(ValidationException.class, () -> sut.update(t));
    }

    @Test
    void getOccurrenceInfoRetrievesAggregatedOccurrenceData() {
        final Term t = Generator.generateTermWithId();
        t.setVocabulary(vocabulary.getUri());

        final File file = Generator.generateFileWithId("test.html");
        final Document document = Generator.generateDocumentWithId();
        document.addFile(file);
        final TermOccurrence occurrence = Generator.generateTermOccurrence(t, file, false);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(document);
            em.persist(t);
            em.persist(file);
            em.persist(occurrence.getTarget());
            em.persist(occurrence);
        });

        final List<TermOccurrences> result = sut.getOccurrenceInfo(t);
        assertEquals(1, result.size());
        assertEquals(t.getUri(), result.get(0).getTerm());
        assertEquals(document.getUri(), result.get(0).getResource());
        assertEquals(document.getLabel(), result.get(0).getResourceLabel());
    }

    @Test
    void updateRemovesTermFromRootTermsWhenParentIsSetForIt() {
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            vocabulary.getGlossary().addRootTerm(child);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(child, vocabulary.getUri(), em);
        });
        child.addParentTerm(parent);
        // This is normally inferred
        child.setVocabulary(vocabulary.getUri());
        sut.update(child);

        final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri(),
                                        descriptorFactory.glossaryDescriptor(vocabulary));
        assertTrue(result.getRootTerms().contains(parent.getUri()));
        assertFalse(result.getRootTerms().contains(child.getUri()));
    }

    @Test
    void updateAddsTermToRootTermsWhenParentIsRemovedFromIt() {
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.setGlossary(vocabulary.getGlossary().getUri());
        child.addParentTerm(parent);
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(child, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
        });
        child.setParentTerms(null);
        // This is normally inferred
        child.setVocabulary(vocabulary.getUri());
        sut.update(child);

        final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri(),
                                        descriptorFactory.glossaryDescriptor(vocabulary));
        assertTrue(result.getRootTerms().contains(parent.getUri()));
        assertTrue(result.getRootTerms().contains(child.getUri()));
    }

    @Test
    void addChildTermAllowsAddingChildTermToDifferentVocabularyThanParent() {
        final Term parentTerm = generateParentTermFromDifferentVocabulary();
        final Term childTerm = Generator.generateTermWithId();
        childTerm.setVocabulary(childVocabulary.getUri());

        sut.addChildTerm(childTerm, parentTerm);
        final Term result = em.find(Term.class, childTerm.getUri(), descriptorFactory.termDescriptor(childVocabulary));
        assertEquals(childTerm, result);
        assertEquals(childVocabulary.getGlossary().getUri(), childTerm.getGlossary());
        final Glossary childGlossary = em.find(Glossary.class, childVocabulary.getGlossary().getUri(),
                                               descriptorFactory.glossaryDescriptor(childVocabulary));
        assertThat(childGlossary.getRootTerms(), hasItem(childTerm.getUri()));
    }

    private Term generateParentTermFromDifferentVocabulary() {
        final Term parentTerm = Generator.generateTermWithId();

        transactional(() -> {
            parentTerm.setGlossary(vocabulary.getGlossary().getUri());
            vocabulary.getGlossary().addRootTerm(parentTerm);
            em.persist(parentTerm, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(parentTerm, vocabulary.getUri(), em);
        });
        // This is normally inferred
        parentTerm.setVocabulary(vocabulary.getUri());
        return parentTerm;
    }

    @Test
    void addChildTermUsesTermVocabularyWhenGeneratingUri() {
        final Term parentTerm = generateParentTermFromDifferentVocabulary();
        final Term childTerm = Generator.generateTerm();
        childTerm.setVocabulary(childVocabulary.getUri());
        sut.addChildTerm(childTerm, parentTerm);

        assertThat(childTerm.getUri().toString(), startsWith(childVocabulary.getUri().toString()));
    }

    @Test
    void addChildTermSetsItAsVocabularyRootTermWhenParentIsFromDifferentVocabulary() {
        final Term parentTerm = generateParentTermFromDifferentVocabulary();
        final Term childTerm = Generator.generateTermWithId();
        childTerm.setVocabulary(childVocabulary.getUri());
        sut.addChildTerm(childTerm, parentTerm);

        assertThat(sut.findAllRoots(childVocabulary, Constants.DEFAULT_PAGE_SPEC, Collections
                .emptyList()), hasItem(new TermDto(childTerm)));
        final Glossary result = em.find(Glossary.class, childVocabulary.getGlossary().getUri());
        assertTrue(result.getRootTerms().contains(childTerm.getUri()));
    }

    @Test
    void updateAddsTermToGlossaryRootTermsWhenNewParentIsFromDifferentVocabulary() {
        final Term newParentTerm = generateParentTermFromDifferentVocabulary();
        final Term oldParentTerm = Generator.generateTermWithId();
        oldParentTerm.setGlossary(childVocabulary.getGlossary().getUri());
        final Term childTerm = Generator.generateTermWithId();
        childTerm.addParentTerm(oldParentTerm);
        childTerm.setGlossary(childVocabulary.getGlossary().getUri());
        transactional(() -> {
            childVocabulary.getGlossary().addRootTerm(oldParentTerm);
            em.persist(oldParentTerm, descriptorFactory.termDescriptor(childVocabulary));
            em.persist(childTerm, descriptorFactory.termDescriptor(childVocabulary));
            em.merge(childVocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(childVocabulary));
            Generator.addTermInVocabularyRelationship(childTerm, childVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(oldParentTerm, childVocabulary.getUri(), em);
        });

        // This is normally inferred
        childTerm.setVocabulary(childVocabulary.getUri());
        em.getEntityManagerFactory().getCache().evictAll();
        childTerm.setExternalParentTerms(Collections.singleton(newParentTerm));
        sut.update(childTerm);
        assertTrue(sut.findAllRoots(childVocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList())
                      .contains(new TermDto(childTerm)));
        final Glossary result = em.find(Glossary.class, childVocabulary.getGlossary().getUri());
        assertTrue(result.getRootTerms().contains(childTerm.getUri()));
    }

    @Test
    void removeRemovesNonReferencedNonOccurringTerm() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(term));
        });
        sut.remove(term);
        final Term result = em.find(Term.class, term.getUri());
        assertNull(result);
    }

    private void generateRelatedInverse(Term term, Term related, String property) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.add(vf.createIRI(related.getUri().toString()), vf.createIRI(property), vf
                    .createIRI(term.getUri().toString()));
        }
    }

    @Test
    void updateDifferentiatesAssertedAndInverseRelatedTermsBeforeMergingStateIntoRepository() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term related = Generator.generateTermWithId(vocabulary.getUri());
        final Term inverseRelated = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        related.setGlossary(vocabulary.getGlossary().getUri());
        term.addRelatedTerm(new TermInfo(related));
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(related, descriptorFactory.termDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(inverseRelated, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(related, vocabulary.getUri(), em);
            generateRelatedInverse(term, inverseRelated, SKOS.RELATED);
        });

        term.addRelatedTerm(new TermInfo(inverseRelated));
        term.getLabel().set("cs", "Test aktualizace");
        sut.update(term);

        final Term result = em.find(Term.class, term.getUri());
        assertEquals(Collections.singleton(new TermInfo(related)), result.getRelated());
    }

    @Test
    void updateDeletesRelatedRelationshipFromOtherSideWhenItWasRemovedFromTargetTerm() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term inverseRelated = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(inverseRelated, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(inverseRelated, vocabulary.getUri(), em);
            generateRelatedInverse(term, inverseRelated, SKOS.RELATED);
        });

        term.setRelated(Collections.emptySet());
        sut.update(term);
        final Term inverseResult = em.find(Term.class, inverseRelated.getUri());
        assertThat(inverseResult.getRelated(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
    }

    @Test
    void updatesDeletesRelatedMatchRelationshipFromOtherSideWhenItWasRemovedFromTargetTerm() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term inverseRelatedMatch = Generator.generateTermWithId(childVocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(inverseRelatedMatch, descriptorFactory.termDescriptor(childVocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(inverseRelatedMatch, childVocabulary.getUri(), em);
            generateRelatedInverse(term, inverseRelatedMatch, SKOS.RELATED_MATCH);
        });

        term.setRelatedMatch(Collections.emptySet());
        sut.update(term);
        final Term inverseResult = em.find(Term.class, inverseRelatedMatch.getUri());
        assertThat(inverseResult.getRelatedMatch(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
    }

    @Test
    void updateDeletesExactMatchRelationshipsFromOtherSideWhenItWasRemovedFromTargetTerm() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term inverseExactMatch = Generator.generateTermWithId(childVocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(inverseExactMatch, descriptorFactory.termDescriptor(childVocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(inverseExactMatch, childVocabulary.getUri(), em);
            generateRelatedInverse(term, inverseExactMatch, SKOS.EXACT_MATCH);
        });

        term.setExactMatchTerms(Collections.emptySet());
        sut.update(term);
        final Term inverseResult = em.find(Term.class, inverseExactMatch.getUri());
        assertThat(inverseResult.getExactMatchTerms(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
    }

    @Test
    void preUpdateSplitsExternalAndInternalTermParents() {
        final Term term = Generator.generateTermWithId(childVocabulary.getUri());
        term.setGlossary(childVocabulary.getGlossary().getUri());
        final Term internalParent = Generator.generateTermWithId(childVocabulary.getUri());
        internalParent.setGlossary(childVocabulary.getGlossary().getUri());
        final Term externalParent = Generator.generateTermWithId(vocabulary.getUri());
        externalParent.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(term));
            em.persist(internalParent, descriptorFactory.termDescriptor(internalParent));
            em.persist(externalParent, descriptorFactory.termDescriptor(externalParent));
            Generator.addTermInVocabularyRelationship(term, childVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(internalParent, childVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(externalParent, vocabulary.getUri(), em);
        });

        term.setParentTerms(new HashSet<>(Arrays.asList(internalParent, externalParent)));
        sut.update(term);

        final Term result = em.find(Term.class, term.getUri());
        assertThat(result.getParentTerms(), hasItem(internalParent));
        assertThat(result.getExternalParentTerms(), hasItem(externalParent));
    }

    @Test
    void addRootTermToVocabularySplitsExternalAndInternalTermParents() {
        final Term term = Generator.generateTermWithId(childVocabulary.getUri());
        term.setGlossary(childVocabulary.getGlossary().getUri());
        final Term externalParent = Generator.generateTermWithId(vocabulary.getUri());
        externalParent.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(externalParent, descriptorFactory.termDescriptor(externalParent));
            Generator.addTermInVocabularyRelationship(externalParent, vocabulary.getUri(), em);
        });

        term.setParentTerms(Collections.singleton(externalParent));
        sut.addRootTermToVocabulary(term, childVocabulary);

        final Term result = em.find(Term.class, term.getUri());
        assertThat(result.getExternalParentTerms(), hasItem(externalParent));
    }

    @Test
    void addChildTermSplitsExternalAndInternalTermParents() {
        final Term term = Generator.generateTermWithId(childVocabulary.getUri());
        term.setGlossary(childVocabulary.getGlossary().getUri());
        final Term internalParent = Generator.generateTermWithId(childVocabulary.getUri());
        internalParent.setGlossary(childVocabulary.getGlossary().getUri());
        final Term externalParent = Generator.generateTermWithId(vocabulary.getUri());
        externalParent.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(internalParent, descriptorFactory.termDescriptor(internalParent));
            em.persist(externalParent, descriptorFactory.termDescriptor(externalParent));
            Generator.addTermInVocabularyRelationship(internalParent, childVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(externalParent, vocabulary.getUri(), em);
        });

        term.setParentTerms(new HashSet<>(Arrays.asList(internalParent, externalParent)));
        sut.addChildTerm(term, internalParent);

        final Term result = em.find(Term.class, term.getUri());
        assertThat(result.getParentTerms(), hasItem(internalParent));
        assertThat(result.getExternalParentTerms(), hasItem(externalParent));
    }

    @Test
    void setStateToDraftSetsTermStateAndUpdatesIt() {
        final Term term = generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(term)));
        final URI state = Generator.randomItem(Generator.TERM_STATES);
        sut.setState(term, state);

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(state, result.getState());
    }

    @Test
    void setStatusDoesUpdateInCorrectContext() {
        final Term term = generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(term)));
        final URI state = Generator.randomItem(Generator.TERM_STATES);
        sut.setState(term, state);

        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                assertTrue(conn.hasStatement(vf.createIRI(term.getUri().toString()), vf.createIRI(
                                cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_stav_pojmu), null, false,
                        vf.createIRI(vocabulary.getUri().toString())));
                assertEquals(1,
                        conn.getStatements(vf.createIRI(term.getUri().toString()), vf.createIRI(
                                cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_stav_pojmu), null).stream().count());
            }
        });
    }

    /**
     * Bug kbss-cvut/termit-ui#282
     */
    @Test
    void removingExactMatchFromInverseSideWorksInTransaction() {
        enableRdfsInference(em);
        final Term term = generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setVocabulary(vocabulary.getUri());
        final Term exactMatch = generateTermWithId();
        exactMatch.setGlossary(childVocabulary.getGlossary().getUri());
        exactMatch.setVocabulary(childVocabulary.getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(term));
            em.persist(exactMatch, descriptorFactory.termDescriptor(exactMatch));
        });
        transactional(() -> {
            term.addExactMatch(new TermInfo(exactMatch));
            em.merge(term, descriptorFactory.termDescriptor(term));
        });

        transactional(() -> {
            // This simulates what happens in TermService
            final Term original = sut.findRequired(exactMatch.getUri());
            assertFalse(original.getInverseExactMatchTerms().isEmpty());
            exactMatch.setExactMatchTerms(null);
            sut.update(exactMatch);
        });

        final Term resultExactMatch = em.find(Term.class, exactMatch.getUri());
        assertThat(resultExactMatch.getExactMatchTerms(), anyOf(nullValue(), emptyCollectionOf(TermInfo.class)));
        assertThat(resultExactMatch.getInverseExactMatchTerms(), anyOf(nullValue(), emptyCollectionOf(TermInfo.class)));
        final Term resultTerm = em.find(Term.class, term.getUri());
        assertThat(resultTerm.getExactMatchTerms(), anyOf(nullValue(), emptyCollectionOf(TermInfo.class)));
    }

    @Test
    void updatePrunesEmptyTranslationsInMultilingualAttributes() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(term)));
        term.getLabel().set(Environment.LANGUAGE, "update");
        term.getLabel().set("cs", "");
        final MultilingualString expected = new MultilingualString(term.getLabel().getValue());
        expected.remove("cs");
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri());
        assertEquals(expected, result.getLabel());
    }

    @Test
    void persistPreparationPrunesEmptyTranslationsInMultilingualAttributes() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.getDefinition().set(Environment.LANGUAGE, "   ");
        term.getDefinition().set("cs", "Test");
        final MultilingualString expected = new MultilingualString(term.getDefinition().getValue());
        expected.remove(Environment.LANGUAGE);

        sut.addRootTermToVocabulary(term, vocabulary);

        final Term result = em.find(Term.class, term.getUri());
        assertEquals(expected, result.getDefinition());
    }

    @Test
    void removeRemovesHasTopConceptReferenceToRemovedTerm() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(term));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        sut.remove(term);
        assertNull(em.find(Term.class, term.getUri()));
        assertFalse(em.createNativeQuery("ASK { ?glossary ?hasTopConcept ?term . }", Boolean.class)
                            .setParameter("glossary", vocabulary.getGlossary())
                            .setParameter("hasTopConcept", URI.create(SKOS.HAS_TOP_CONCEPT))
                            .setParameter("term", term).getSingleResult());
    }
}
