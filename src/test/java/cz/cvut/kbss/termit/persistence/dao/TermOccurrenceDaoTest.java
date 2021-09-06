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
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.*;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TermOccurrenceDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermOccurrenceDao sut;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        Environment.setCurrentUser(user);
        transactional(() -> em.persist(user));
        enableRdfsInference(em);
    }

    @Test
    void findAllFindsOccurrencesOfTerm() {
        final Map<Term, List<TermOccurrence>> map = generateOccurrences(false);
        final Term term = map.keySet().iterator().next();
        final List<TermOccurrence> occurrences = map.get(term);

        final List<TermOccurrence> result = sut.findAllOf(term);
        assertEquals(occurrences.size(), result.size());
        for (TermOccurrence to : result) {
            assertTrue(occurrences.stream().anyMatch(o -> o.getUri().equals(to.getUri())));
        }
    }

    private Map<Term, List<TermOccurrence>> generateOccurrences(boolean suggested, File... files) {
        final File[] filesToProcess;
        if (files.length == 0) {
            final File file = new File();
            file.setLabel("test.html");
            filesToProcess = new File[]{file};
        } else {
            filesToProcess = files;
        }
        for (File f : filesToProcess) {
            f.setUri(Generator.generateUri());
        }
        final Term tOne = new Term();
        tOne.setUri(Generator.generateUri());
        tOne.setPrimaryLabel("Term one");
        final Term tTwo = new Term();
        tTwo.setUri(Generator.generateUri());
        tTwo.setPrimaryLabel("Term two");
        final Map<Term, List<TermOccurrence>> map = new HashMap<>();
        map.put(tOne, new ArrayList<>());
        map.put(tTwo, new ArrayList<>());
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final TermOccurrence to = new TermFileOccurrence();
            if (suggested) {
                to.addType(Vocabulary.s_c_navrzeny_vyskyt_termu);
            }
            final FileOccurrenceTarget target = new FileOccurrenceTarget(filesToProcess.length > 1 ?
                                                                         filesToProcess[Generator
                                                                                 .randomInt(0, filesToProcess.length)] :
                                                                         filesToProcess[0]);
            final TextQuoteSelector selector = new TextQuoteSelector("test");
            selector.setPrefix("this is a ");
            selector.setSuffix(".");
            target.setSelectors(Collections.singleton(selector));
            to.setTarget(target);
            if (Generator.randomBoolean()) {
                to.setTerm(tOne.getUri());
                map.get(tOne).add(to);
            } else {
                to.setTerm(tTwo.getUri());
                map.get(tTwo).add(to);
            }
        }
        transactional(() -> {
            for (File f : filesToProcess) {
                em.persist(f);
            }
            map.forEach((t, list) -> {
                em.persist(t);
                list.forEach(ta -> {
                    em.persist(ta.getTarget());
                    em.persist(ta);
                });
            });
        });
        return map;
    }

    @Test
    void findAllInFileReturnsTermOccurrencesWithTargetFile() {
        final File fOne = new File();
        fOne.setLabel("fOne.html");
        final File fTwo = new File();
        fTwo.setLabel("fTwo.html");
        final Map<Term, List<TermOccurrence>> allOccurrences = generateOccurrences(false, fOne, fTwo);
        final List<TermOccurrence> matching = allOccurrences.values().stream().flatMap(
                l -> l.stream().filter(to -> to.getTarget().getSource().equals(fOne.getUri())))
                                                            .collect(Collectors.toList());

        em.getEntityManagerFactory().getCache().evictAll();
        final List<TermOccurrence> result = sut.findAllTargeting(fOne);
        assertEquals(matching.size(), result.size());
        for (TermOccurrence to : result) {
            assertTrue(matching.stream().anyMatch(p -> to.getUri().equals(p.getUri())));
        }
    }

    @Test
    void findAllTargetingFileCorrectlyReconstructsOccurrencesWithTargetAndSelectors() {
        enableRdfsInference(em);
        final File fOne = new File();
        fOne.setLabel("fOne.html");
        final File fTwo = new File();
        fTwo.setLabel("fTwo.html");
        generateOccurrences(false, fOne);

        em.getEntityManagerFactory().getCache().evictAll();
        final List<TermOccurrence> result = sut.findAllTargeting(fOne);
        assertFalse(result.isEmpty());
        for (TermOccurrence to : result) {
            assertNotNull(to.getTarget());
            assertNotNull(to.getTarget().getUri());
            assertFalse(to.getTarget().getSelectors().isEmpty());
            to.getTarget().getSelectors().forEach(sel -> {
                assertNotNull(sel.getUri());
                assertThat(sel, instanceOf(TextQuoteSelector.class));
                final TextQuoteSelector textQuoteSelector = (TextQuoteSelector) sel;
                assertNotNull(textQuoteSelector.getExactMatch());
                assertNotEquals(textQuoteSelector.getPrefix(), textQuoteSelector.getSuffix());
            });
        }
    }

    @Test
    void findAllReturnsEmptyListWhenNoOccurrencesAreFound() {
        enableRdfsInference(em);
        final File fTwo = new File();
        fTwo.setLabel("fTwo.html");
        fTwo.setUri(Generator.generateUri());
        transactional(() -> em.persist(fTwo));

        em.getEntityManagerFactory().getCache().evictAll();
        final List<TermOccurrence> result = sut.findAllTargeting(fTwo);
        assertTrue(result.isEmpty());
    }

    @Test
    void removeSuggestedRemovesOccurrencesForFile() {
        final File file = new File();
        file.setLabel("test.html");
        generateOccurrences(true, file);
        assertFalse(sut.findAllTargeting(file).isEmpty());
        transactional(() -> sut.removeSuggested(file));
        assertTrue(sut.findAllTargeting(file).isEmpty());
        assertFalse(em.createNativeQuery("ASK { ?x a ?termOccurrence . }", Boolean.class).setParameter("termOccurrence",
                URI.create(Vocabulary.s_c_vyskyt_termu)).getSingleResult());
    }

    @Test
    void removeSuggestedRetainsConfirmedOccurrences() {
        final File file = new File();
        file.setLabel("test.html");
        final Map<Term, List<TermOccurrence>> allOccurrences = generateOccurrences(true, file);
        assertFalse(sut.findAllTargeting(file).isEmpty());
        final List<TermOccurrence> retained = new ArrayList<>();
        transactional(() -> allOccurrences
                .forEach((t, list) -> list.stream().filter(to -> Generator.randomBoolean()).forEach(to -> {
                    to.removeType(Vocabulary.s_c_navrzeny_vyskyt_termu);
                    retained.add(to);
                    em.merge(to);
                })));
        transactional(() -> sut.removeSuggested(file));
        final List<TermOccurrence> result = sut.findAllTargeting(file);
        assertEquals(retained.size(), result.size());
        result.forEach(to -> assertTrue(retained.stream().anyMatch(toExp -> toExp.getUri().equals(to.getUri()))));
    }

    @Test
    void removeAllRemovesSuggestedAndConfirmedOccurrences() {
        final File file = new File();
        file.setLabel("test.html");
        final Map<Term, List<TermOccurrence>> allOccurrences = generateOccurrences(true, file);
        assertFalse(sut.findAllTargeting(file).isEmpty());
        transactional(() -> allOccurrences
                .forEach((t, list) -> list.stream().filter(to -> Generator.randomBoolean()).forEach(to -> {
                    to.removeType(Vocabulary.s_c_navrzeny_vyskyt_termu);
                    em.merge(to);
                })));
        transactional(() -> sut.removeAll(file));
        assertTrue(sut.findAllTargeting(file).isEmpty());
        assertFalse(em.createNativeQuery("ASK { ?x a ?termOccurrence . }", Boolean.class).setParameter("termOccurrence",
                URI.create(Vocabulary.s_c_vyskyt_termu)).getSingleResult());
    }

    @Test
    void removeAllRemovesAlsoOccurrenceTargets() {
        final File file = new File();
        file.setLabel("test.html");
        final Map<Term, List<TermOccurrence>> allOccurrences = generateOccurrences(true, file);
        assertFalse(sut.findAllTargeting(file).isEmpty());
        transactional(() -> allOccurrences
                .forEach((t, list) -> list.forEach(to -> {
                    to.removeType(Vocabulary.s_c_navrzeny_vyskyt_termu);
                    em.merge(to);
                })));
        transactional(() -> sut.removeAll(file));

        assertFalse(em.createNativeQuery("ASK { ?x a ?target . }", Boolean.class).setParameter("target",
                URI.create(Vocabulary.s_c_cil)).getSingleResult());
    }

    @Test
    void findAllDefinitionalOfReturnsDefinitionalOccurrencesOfSpecifiedTerm() {
        final Term term = Generator.generateTermWithId();
        final Term otherTerm = Generator.generateTermWithId();
        generateDefinitionalOccurrence(term, otherTerm);

        final List<TermOccurrence> result = sut.findAllDefinitionalOf(term);
        assertFalse(result.isEmpty());
        result.forEach(to -> {
            assertEquals(term.getUri(), to.getTerm());
            assertEquals(otherTerm.getUri(), to.getTarget().getSource());
        });
    }

    private void generateDefinitionalOccurrence(Term of, Term in) {
        final DefinitionalOccurrenceTarget target = new DefinitionalOccurrenceTarget(in);
        target.setSelectors(Collections.singleton(new TextQuoteSelector("test")));
        final TermOccurrence occurrence = new TermDefinitionalOccurrence(of.getUri(), target);
        transactional(() -> {
            em.persist(occurrence);
            em.persist(target);
        });
    }

    @Test
    void findAllDefinitionalOfReturnsOnlyDefinitionalOccurrencesOfSpecifiedTerm() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId();
        final Term otherTerm = Generator.generateTermWithId();
        generateDefinitionalOccurrence(term, otherTerm);
        generateFileOccurrence(term);

        final List<TermOccurrence> result = sut.findAllDefinitionalOf(term);
        assertEquals(1, result.size());
        result.forEach(to -> assertEquals(otherTerm.getUri(), to.getTarget().getSource()));
    }

    private void generateFileOccurrence(Term of) {
        final File file = Generator.generateFileWithId("test.html");
        final FileOccurrenceTarget target = new FileOccurrenceTarget(file);
        target.setSelectors(Collections.singleton(new TextQuoteSelector("test")));
        final TermFileOccurrence occurrence = new TermFileOccurrence(of.getUri(), target);
        transactional(() -> {
            em.persist(file);
            em.persist(occurrence);
            em.persist(target);
        });
    }
}
