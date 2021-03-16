/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.dto.TermDto;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.assignment.Target;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

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
        term.getLabel().remove(Constants.DEFAULT_LANGUAGE);

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
    void findAllRootsReturnsRootTermsOnMatchingPage() {
        final List<Term> terms = Generator.generateTermsWithIds(10);
        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            terms.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(vocabulary)));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        final List<TermDto> resultOne = sut.findAllRoots(vocabulary, PageRequest.of(0, 5), Collections.emptyList());
        final List<TermDto> resultTwo = sut.findAllRoots(vocabulary, PageRequest.of(1, 5), Collections.emptyList());

        assertEquals(5, resultOne.size());
        assertEquals(5, resultTwo.size());

        final List<TermDto> expectedDtos = termsToDtos(terms);
        resultOne.forEach(t -> {
            assertThat(expectedDtos, hasItem(t));
            assertThat(resultTwo, not(hasItem(t)));
        });
        resultTwo.forEach(t -> assertThat(expectedDtos, hasItem(t)));
    }

    @Test
    void findTermsBySearchStringReturnsMatchingTerms() {
        final List<Term> terms = Generator.generateTermsWithIds(10);
        terms.forEach(t -> t.setVocabulary(vocabulary.getUri()));
        final List<Term> matching = terms.subList(0, 5);
        matching.forEach(t -> t.getLabel().set(Constants.DEFAULT_LANGUAGE, "Result + " + t.getLabel()));

        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        final Descriptor termDescriptor = descriptorFactory.termDescriptor(vocabulary);
        transactional(() -> {
            terms.forEach(t -> em.persist(t, termDescriptor));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        List<TermDto> result = sut.findAll("Result", vocabulary);
        assertEquals(matching.size(), result.size());
        assertTrue(termsToDtos(matching).containsAll(result));
    }

    @Test
    void existsInVocabularyChecksForTermWithMatchingLabel() {
        final Term t = Generator.generateTermWithId();
        transactional(() -> {
            t.setVocabulary(vocabulary.getUri());
            vocabulary.getGlossary().addRootTerm(t);
            em.persist(t, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        assertTrue(sut.existsInVocabulary(t.getLabel().get(Constants.DEFAULT_LANGUAGE), vocabulary,
                Constants.DEFAULT_LANGUAGE));
    }

    @Test
    void isEmptyReturnsTrueForEmptyVocabulary() {
        assertTrue(sut.isEmpty(vocabulary));
    }

    @Test
    void isEmptyReturnsFalseForNonemptyVocabulary() {
        final Term t = Generator.generateTermWithId();
        transactional(() -> {
            t.setVocabulary(vocabulary.getUri());
            vocabulary.getGlossary().addRootTerm(t);
            em.persist(t, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        assertFalse(sut.isEmpty(vocabulary));
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
        childOne.getLabel().set(Constants.DEFAULT_LANGUAGE, newLabel);
        // This is normally inferred
        childOne.setVocabulary(vocabulary.getUri());
        transactional(() -> sut.update(childOne));
        final Term result = em.find(Term.class, childOne.getUri());
        assertEquals(newLabel, result.getLabel().get(Constants.DEFAULT_LANGUAGE));
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

        t.getLabel().remove(Constants.DEFAULT_LANGUAGE);
        assertThrows(ValidationException.class, () -> sut.update(t));
    }

    @Test
    void getAssignmentsInfoRetrievesAssignmentData() {
        final Term t = Generator.generateTermWithId();
        t.setVocabulary(vocabulary.getUri());

        final Resource resource = Generator.generateResourceWithId();
        final TermAssignment ta = new TermAssignment();
        ta.setTerm(t.getUri());
        ta.setTarget(new Target(resource));
        transactional(() -> {
            em.persist(t);
            em.persist(resource);
            em.persist(ta.getTarget());
            em.persist(ta);
        });

        final List<TermAssignments> result = sut.getAssignmentsInfo(t);
        assertEquals(1, result.size());
        assertEquals(t.getUri(), result.get(0).getTerm());
        assertEquals(resource.getUri(), result.get(0).getResource());
        assertEquals(resource.getLabel(), result.get(0).getResourceLabel());
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
    void findAllRootsIncludingImportsReturnsRootTermsOnMatchingPage() {
        final List<Term> terms = Generator.generateTermsWithIds(10);
        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            terms.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(vocabulary)));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        final List<TermDto> resultOne = sut
                .findAllRootsIncludingImported(vocabulary, PageRequest.of(0, 5), Collections.emptyList());
        final List<TermDto> resultTwo = sut
                .findAllRootsIncludingImported(vocabulary, PageRequest.of(1, 5), Collections.emptyList());

        assertEquals(5, resultOne.size());
        assertEquals(5, resultTwo.size());

        final List<TermDto> expectedDtos = termsToDtos(terms);
        resultOne.forEach(t -> {
            assertThat(expectedDtos, hasItem(t));
            assertThat(resultTwo, not(hasItem(t)));
        });
        resultTwo.forEach(t -> assertThat(expectedDtos, hasItem(t)));
    }

    @Test
    void findAllIncludingImportedBySearchStringReturnsMatchingTerms() {
        final List<Term> terms = Generator.generateTermsWithIds(10);
        final String searchString = "Result";
        final List<Term> matching = terms.subList(0, 5);
        matching.forEach(t -> t.getLabel().set(Constants.DEFAULT_LANGUAGE, searchString + " " + t.getLabel()));

        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        terms.forEach(t -> t.setVocabulary(vocabulary.getUri()));
        final Descriptor termDescriptor = descriptorFactory.termDescriptor(vocabulary);
        transactional(() -> {
            terms.forEach(t -> em.persist(t, termDescriptor));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        List<TermDto> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertEquals(matching.size(), result.size());
        assertTrue(termsToDtos(matching).containsAll(result));
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

        assertThat(sut.findAllRoots(childVocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList()), hasItem(new TermDto(childTerm)));
        final Glossary result = em.find(Glossary.class, childVocabulary.getGlossary().getUri());
        assertTrue(result.getRootTerms().contains(childTerm.getUri()));
    }

    @Test
    void updateAddsTermToGlossaryRootTermsWhenNewParentIsFromDifferentVocabulary() {
        final Term newParentTerm = generateParentTermFromDifferentVocabulary();
        final Term oldParentTerm = Generator.generateTermWithId();
        final Term childTerm = Generator.generateTermWithId();
        childTerm.addParentTerm(oldParentTerm);
        childTerm.setGlossary(childVocabulary.getGlossary().getUri());
        transactional(() -> {
            childVocabulary.getGlossary().addRootTerm(oldParentTerm);
            oldParentTerm.setGlossary(childVocabulary.getGlossary().getUri());
            em.persist(oldParentTerm, descriptorFactory.termDescriptor(childVocabulary));
            em.persist(childTerm, descriptorFactory.termDescriptor(childVocabulary));
            em.merge(childVocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(childVocabulary));
            Generator.addTermInVocabularyRelationship(childTerm, childVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(oldParentTerm, childVocabulary.getUri(), em);
        });

        // This is normally inferred
        childTerm.setVocabulary(childVocabulary.getUri());
        em.getEntityManagerFactory().getCache().evictAll();
        childTerm.setParentTerms(Collections.singleton(newParentTerm));
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
}
