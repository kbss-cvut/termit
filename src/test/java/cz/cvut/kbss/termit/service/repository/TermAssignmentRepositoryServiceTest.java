/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.Target;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermAssignmentRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermAssignmentRepositoryService sut;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    @Test
    void findAllRetrievesAssignmentsForSpecifiedResource() {
        final Resource resource = Generator.generateResourceWithId();
        final Target target = new Target(resource);
        final Term term = Generator.generateTermWithId();
        final TermAssignment ta = new TermAssignment(term.getUri(), target);
        transactional(() -> {
            em.persist(target);
            em.persist(resource);
            em.persist(term);
            em.persist(ta);
        });

        final List<TermAssignment> result = sut.findAll(resource);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ta.getUri(), result.get(0).getUri());
    }

    @Test
    void removeAllDeletesAssignmentsOfSpecifiedResource() {
        final Resource resource = Generator.generateResourceWithId();
        final Target target = new Target(resource);
        final Term term = Generator.generateTermWithId();
        final TermAssignment ta = new TermAssignment(term.getUri(), target);
        transactional(() -> {
            em.persist(target);
            em.persist(resource);
            em.persist(term);
            em.persist(ta);
        });

        sut.removeAll(resource);
        verifyInstancesDoNotExist(Vocabulary.s_c_prirazeni_termu, em);
        verifyInstancesDoNotExist(Vocabulary.s_c_cil, em);
    }

    @Test
    void setOnResourceThrowsNotFoundForUnknownTermIdentifiers() {
        assertThrows(NotFoundException.class, () -> {
            final Resource resource = generateResource();
            final Set<URI> terms = new HashSet<>();
            terms.add(URI.create("http://unknown.uri/term1"));
            terms.add(URI.create("http://unknown.uri/term2"));
            transactional(() -> sut.setOnResource(resource, terms));
        });
    }

    private Resource generateResource() {
        final Resource resource = Generator.generateResourceWithId();
        transactional(() -> em.persist(resource));
        return resource;
    }

    @Test
    void setOnResourceAddsAssignmentsToUnTaggedResource() {
        final Resource resource = generateResource();

        final Set<URI> tags = new HashSet<>();
        final URI term0 = generateTermWithUriAndPersist().getUri();
        final URI term1 = generateTermWithUriAndPersist().getUri();
        tags.add(term0);
        tags.add(term1);

        transactional(() -> sut.setOnResource(resource, tags));

        verifyAssignments(resource, tags);
    }

    private Term generateTermWithUriAndPersist() {
        final Term t = Generator.generateTerm();
        t.setUri(Generator.generateUri());
        transactional(() -> em.persist(t));
        return t;
    }

    private void verifyAssignments(Resource resource, Collection<URI> expectedTerms) {
        final List<TermAssignment> result = sut.findAll(resource);
        assertEquals(expectedTerms.size(), result.size());
        assertEquals(expectedTerms, result.stream().map(TermAssignment::getTerm).collect(Collectors.toSet()));
    }

    @Test
    void setOnResourceReplacesExistingObsoleteAssignments() {
        final Resource resource = generateResource();

        final Set<URI> tags = new HashSet<>();
        final URI term0 = generateTermWithUriAndPersist().getUri();
        final URI term1 = generateTermWithUriAndPersist().getUri();
        tags.add(term0);
        tags.add(term1);

        transactional(() -> sut.setOnResource(resource, tags));

        final Set<URI> tags2 = new HashSet<>();
        final URI term2 = generateTermWithUriAndPersist().getUri();
        final URI term3 = generateTermWithUriAndPersist().getUri();
        tags2.add(term2);
        tags2.add(term3);
        transactional(() -> sut.setOnResource(resource, tags2));

        verifyAssignments(resource, tags2);
    }

    @Test
    void setOnResourceMergesExistingAssignmentsAndNewlySpecifiedTags() {
        final Resource resource = generateResource();

        final Set<URI> tags = new HashSet<>();
        final URI term0 = generateTermWithUriAndPersist().getUri();
        final URI term1 = generateTermWithUriAndPersist().getUri();
        tags.add(term0);
        tags.add(term1);

        transactional(() -> sut.setOnResource(resource, tags));
        final Set<URI> tags2 = new HashSet<>();
        final URI term2 = generateTermWithUriAndPersist().getUri();
        tags2.add(term0);
        tags2.add(term2);

        transactional(() -> sut.setOnResource(resource, tags2));

        verifyAssignments(resource, tags2);
    }

    @Test
    void addToResourceAddsAssignmentsToUntaggedResource() {
        final Resource resource = generateResource();

        final Set<URI> tags = new HashSet<>();
        final URI term0 = generateTermWithUriAndPersist().getUri();
        final URI term1 = generateTermWithUriAndPersist().getUri();
        tags.add(term0);
        tags.add(term1);

        transactional(() -> sut.addToResource(resource, tags));

        verifyAssignments(resource, tags);
    }

    @Test
    void addToResourceAddsAssignmentsForNewTermsButKeepsExisting() {
        final Resource resource = generateResource();

        final Set<URI> tags = new HashSet<>();
        final URI term0 = generateTermWithUriAndPersist().getUri();
        final URI term1 = generateTermWithUriAndPersist().getUri();
        tags.add(term0);
        tags.add(term1);

        transactional(() -> sut.setOnResource(resource, tags));
        final Set<URI> tags2 = new HashSet<>();
        final URI term2 = generateTermWithUriAndPersist().getUri();
        tags2.add(term0);
        tags2.add(term2);
        sut.addToResource(resource, tags2);

        final Set<URI> expected = new HashSet<>(tags);
        expected.addAll(tags2);
        verifyAssignments(resource, expected);
    }

    @Test
    void addToResourceDoesNothingWhenSpecifiedTermCollectionIsEmpty() {
        final Resource resource = generateResource();

        sut.addToResource(resource, Collections.emptySet());
        verifyInstancesDoNotExist(Vocabulary.s_c_cil, em);
    }

    @Test
    void addToResourceSuggestedCreatesAssignmentsWithSuggestedType() {
        final Resource resource = generateResource();

        final Set<URI> tags = new HashSet<>();
        final URI term0 = generateTermWithUriAndPersist().getUri();
        final URI term1 = generateTermWithUriAndPersist().getUri();
        tags.add(term0);
        tags.add(term1);

        transactional(() -> sut.addToResourceSuggested(resource, tags));
        final List<TermAssignment> result = sut.findAll(resource);
        assertEquals(tags.size(), result.size());
        result.forEach(ta -> assertTrue(ta.getTypes().contains(Vocabulary.s_c_navrzene_prirazeni_termu)));
    }

    @Test
    void persistOccurrenceSavesSpecifiedOccurrenceIntoRepository() {
        final Term term = Generator.generateTermWithId();
        final File resource = Generator.generateFileWithId("test.html");
        transactional(() -> {
            em.persist(term);
            em.persist(resource);
        });
        final TermDefinitionSource definitionSource = new TermDefinitionSource(term.getUri(),
                new FileOccurrenceTarget(resource));
        definitionSource.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test")));

        transactional(() -> sut.persistOccurrence(definitionSource));
        final TermDefinitionSource result = em.find(TermDefinitionSource.class, definitionSource.getUri());
        assertNotNull(result);
        assertEquals(term.getUri(), result.getTerm());
        assertEquals(resource.getUri(), result.getTarget().getSource());
    }

    @Test
    void setOnResourceRemovesAllAssignmentsWhenEmptyInputCollectionIsProvided() {
        final Resource resource = generateResource();

        final Set<URI> tags = new HashSet<>();
        final URI term0 = generateTermWithUriAndPersist().getUri();
        final URI term1 = generateTermWithUriAndPersist().getUri();
        tags.add(term0);
        tags.add(term1);

        transactional(() -> sut.setOnResource(resource, tags));
        verifyAssignments(resource, tags);

        transactional(() -> sut.setOnResource(resource, Collections.emptySet()));
        verifyAssignments(resource, Collections.emptySet());
    }
}
