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
import cz.cvut.kbss.jopa.model.JOPAPersistenceProperties;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermFileOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermOccurrenceDaoTest extends BaseDaoTestRunner {

    private static final String FILE_LABEL = "test.html";

    @Autowired
    private EntityManager em;

//    @Autowired
//    private ScheduledContextRemover contextRemover;

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
            file.setLabel(FILE_LABEL);
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
        // Ensure every file has an occurrence
        for (File f : filesToProcess) {
            final Term term = Generator.randomBoolean() ? tOne : tTwo;
            final TermOccurrence to = generateTermOccurrence(suggested, f, term);
            map.get(term).add(to);
        }
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final Term term = Generator.randomBoolean() ? tOne : tTwo;
            final TermOccurrence to = generateTermOccurrence(suggested,
                                                             filesToProcess[Generator.randomIndex(filesToProcess)],
                                                             term);
            map.get(term).add(to);
        }
        transactional(() -> {
            for (File f : filesToProcess) {
                em.persist(f);
            }
            map.forEach((t, list) -> {
                em.persist(t);
                list.forEach(ta -> {
                    final Descriptor descriptor = new EntityDescriptor(ta.resolveContext());
                    em.persist(ta.getTarget(), descriptor);
                    em.persist(ta, descriptor);
                });
            });
        });
        return map;
    }

    private TermOccurrence generateTermOccurrence(boolean suggested, File file, Term term) {
        final TermOccurrence to = new TermFileOccurrence();
        if (suggested) {
            to.addType(Vocabulary.s_c_navrzeny_vyskyt_termu);
        }
        final FileOccurrenceTarget target = new FileOccurrenceTarget(file);
        final TextQuoteSelector selector = new TextQuoteSelector("test");
        selector.setPrefix("this is a ");
        selector.setSuffix(".");
        target.setSelectors(Collections.singleton(selector));
        to.setTarget(target);
        to.setTerm(term.getUri());
        return to;
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
                                                            .toList();

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
        generateOccurrences(false, fOne, fTwo);

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
        file.setLabel(FILE_LABEL);
        generateOccurrences(true, file);
        assertFalse(sut.findAllTargeting(file).isEmpty());
        transactional(() -> sut.removeSuggested(file));
        assertTrue(sut.findAllTargeting(file).isEmpty());
        assertFalse(em.createNativeQuery("ASK { ?x a ?termOccurrence . }", Boolean.class).setParameter("termOccurrence",
                                                                                                       URI.create(
                                                                                                               Vocabulary.s_c_vyskyt_termu))
                      .getSingleResult());
    }

    @Test
    void removeSuggestedRetainsConfirmedOccurrences() {
        final File file = new File();
        file.setLabel(FILE_LABEL);
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
        final File file = Generator.generateFileWithId(FILE_LABEL);
        final Map<Term, List<TermOccurrence>> allOccurrences = generateOccurrences(true, file);
        assertFalse(sut.findAllTargeting(file).isEmpty());
        transactional(() -> allOccurrences
                .forEach((t, list) -> list.stream().filter(to -> Generator.randomBoolean()).forEach(to -> {
                    to.removeType(Vocabulary.s_c_navrzeny_vyskyt_termu);
                    em.merge(to);
                })));
        transactional(() -> {
            sut.removeAll(file);
//            contextRemover.runContextRemoval();
        });
        assertTrue(sut.findAllTargeting(file).isEmpty());
        assertFalse(em.createNativeQuery("ASK { ?x a ?termOccurrence . }", Boolean.class).setParameter("termOccurrence",
                                                                                                       URI.create(
                                                                                                               Vocabulary.s_c_vyskyt_termu))
                      .getSingleResult());
    }

    @Test
    void removeAllRemovesAlsoOccurrenceTargets() {
        final File file = new File();
        file.setLabel(FILE_LABEL);
        final Map<Term, List<TermOccurrence>> allOccurrences = generateOccurrences(true, file);
        assertFalse(sut.findAllTargeting(file).isEmpty());
        transactional(() -> allOccurrences
                .forEach((t, list) -> list.forEach(to -> {
                    to.removeType(Vocabulary.s_c_navrzeny_vyskyt_termu);
                    em.merge(to);
                })));
        transactional(() -> {
            sut.removeAll(file);
//            contextRemover.runContextRemoval();
        });

        assertFalse(em.createNativeQuery("ASK { ?x a ?target . }", Boolean.class).setParameter("target",
                                                                                               URI.create(
                                                                                                       Vocabulary.s_c_cil))
                      .getSingleResult());
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
        final TermOccurrence occurrence = Generator.generateTermOccurrence(of, in, false);
        transactional(() -> {
            em.persist(occurrence);
            em.persist(occurrence.getTarget());
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
        final File file = Generator.generateFileWithId(FILE_LABEL);
        final FileOccurrenceTarget target = new FileOccurrenceTarget(file);
        target.setSelectors(Collections.singleton(new TextQuoteSelector("test")));
        final TermFileOccurrence occurrence = new TermFileOccurrence(of.getUri(), target);
        transactional(() -> {
            em.persist(file);
            em.persist(occurrence);
            em.persist(target);
        });
    }

    @Test
    void persistSavesTermOccurrenceWithTargetIntoGeneratedContext() {
        final File file = Generator.generateFileWithId(FILE_LABEL);
        transactional(() -> em.persist(file));
        final TermOccurrence occurrence = new TermFileOccurrence(Generator.generateUri(),
                                                                 new FileOccurrenceTarget(file));
        occurrence.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test")));

        transactional(() -> sut.persist(occurrence));
        assertNotNull(sut.find(occurrence.getUri()));
        assertThat(sut.findAllTargeting(file), not(emptyCollectionOf(TermOccurrence.class)));
        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?x a ?occurrence .} }", Boolean.class)
                     .setParameter("x", occurrence.getUri())
                     .setParameter("occurrence", URI.create(Vocabulary.s_c_souborovy_vyskyt_termu))
                     .getSingleResult());
    }

    @Test
    void removeAllOrphansRemovesOccurrencesWithNonExistentTargetSource() {
        final File file = Generator.generateFileWithId(FILE_LABEL);
        generateOccurrences(true, file);
        transactional(() -> em.remove(em.getReference(File.class, file.getUri())));
        assertFalse(sut.findAllTargeting(file).isEmpty());
        transactional(() -> sut.removeAllOrphans());
        assertTrue(sut.findAllTargeting(file).isEmpty());
    }

    @Test
    void getOccurrenceInfoByTermRetrievesAggregateTermOccurrences() {
        final Term term = Generator.generateTermWithId();
        final File fOne = Generator.generateFileWithId("testOne.html");
        final File fTwo = Generator.generateFileWithId("testTwo.html");
        final Document document = getDocument(fOne, fTwo);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(fOne);
            em.persist(fTwo);
            em.persist(document);
        });
        final List<TermOccurrence> occurrencesOne = generateTermOccurrences(term, fOne, false);
        final List<TermOccurrence> occurrencesTwo = generateTermOccurrences(term, fTwo, true);

        final List<TermOccurrences> result = sut.getOccurrenceInfo(term);
        assertEquals(2, result.size());
        for (TermOccurrences toi : result) {
            if (toi.hasType(Vocabulary.s_c_navrzeny_vyskyt_termu)) {
                assertEquals(occurrencesTwo.size(), toi.getCount().intValue());
            } else {
                assertEquals(occurrencesOne.size(), toi.getCount().intValue());
            }
        }
    }

    private Document getDocument(final File... files) {
        final Document document = Generator.generateDocumentWithId();
        Arrays.stream(files).forEach(document::addFile);
        return document;
    }

    @Test
    void getOccurrenceInfoByTermRetrievesTermOccurrencesAggregatedByDocument() {
        final Term term = Generator.generateTermWithId();
        final File fOne = Generator.generateFileWithId("testOne.html");
        final File fTwo = Generator.generateFileWithId("testTwo.html");
        final Document document = getDocument(fOne, fTwo);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(fOne);
            em.persist(fTwo);
            em.persist(document);
        });
        final List<TermOccurrence> occurrencesOne = generateTermOccurrences(term, fOne, false);
        final List<TermOccurrence> occurrencesTwo = generateTermOccurrences(term, fTwo, false);

        final List<TermOccurrences> result = sut.getOccurrenceInfo(term);
        assertEquals(1, result.size());
        final TermOccurrences toi = result.get(0);
        assertEquals(occurrencesOne.size() + occurrencesTwo.size(), toi.getCount().intValue());
    }

    @Test
    void getOccurrenceInfoByTermRetrievesSeparateInstancesForSuggestedAndAssertedOccurrencesOfSameTerm() {
        final Term term = Generator.generateTermWithId();
        final File file = Generator.generateFileWithId("testOne.html");
        final Document document = getDocument(file);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
        });
        generateTermOccurrences(term, file, false);
        generateTermOccurrences(term, file, true);

        final List<TermOccurrences> result = sut.getOccurrenceInfo(term);
        assertEquals(2, result.size());
        result.forEach(tai -> assertEquals(term.getUri(), tai.getTerm()));
    }

    private List<TermOccurrence> generateTermOccurrences(Term term, Asset<?> target, boolean suggested) {
        final List<TermOccurrence> occurrences = IntStream.range(0, Generator.randomInt(5, 10))
                                                          .mapToObj(i -> Generator.generateTermOccurrence(term, target,
                                                                                                          suggested))
                                                          .collect(Collectors.toList());
        transactional(() -> occurrences.forEach(to -> {
            em.persist(to);
            em.persist(to.getTarget());
        }));
        return occurrences;
    }

    private void saveAssetLabelInOtherLanguage(Asset<?> asset) {
        assertEquals(Environment.LANGUAGE,
                     em.getEntityManagerFactory().getProperties().get(JOPAPersistenceProperties.LANG));
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.add(vf.createStatement(vf.createIRI(asset.getUri().toString()), RDFS.LABEL,
                                        vf.createLiteral("Czech label", "cs")));
        }
    }

    @Test
    void getOccurrenceInfoByTermReturnsOnlyAssignmentsForMatchingResourceLabelLanguage() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument(file);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
            saveAssetLabelInOtherLanguage(file);
        });
        generateTermOccurrences(term, file, false);

        final List<TermOccurrences> result = sut.getOccurrenceInfo(term);
        assertEquals(1, result.size());
    }

    @Test
    void getOccurrenceInfoByTermReturnsDistinguishableFileAndDefinitionalOccurrences() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final Term targetTerm = Generator.generateTermWithId();
        targetTerm.setVocabulary(term.getVocabulary());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument(file);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(targetTerm);
            em.persist(file);
            em.persist(document);
        });
        final List<TermOccurrence> fileOccurrences = generateTermOccurrences(term, file, true);
        final List<TermOccurrence> defOccurrences = generateTermOccurrences(term, targetTerm, true);

        final List<TermOccurrences> result = sut.getOccurrenceInfo(term);
        assertEquals(2, result.size());
        for (TermOccurrences a : result) {
            if (a.getTypes().contains(Vocabulary.s_c_souborovy_vyskyt_termu)) {
                assertEquals(fileOccurrences.size(), a.getCount().intValue());
            } else {
                assertThat(a.getTypes(), hasItem(Vocabulary.s_c_definicni_vyskyt_termu));
                assertEquals(defOccurrences.size(), a.getCount().intValue());
            }
        }
    }

    @Test
    void updateSavesTermOccurrenceInContext() {
        final File file = Generator.generateFileWithId(FILE_LABEL);
        transactional(() -> em.persist(file));
        final TermOccurrence occurrence = new TermFileOccurrence(Generator.generateUri(),
                                                                 new FileOccurrenceTarget(file));
        occurrence.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test")));

        transactional(() -> sut.persist(occurrence));
        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?x a ?occurrence ; ?hasTerm ?term .} }", Boolean.class)
                     .setParameter("g", TermOccurrence.resolveContext(file.getUri()))
                     .setParameter("x", occurrence.getUri())
                     .setParameter("occurrence", URI.create(Vocabulary.s_c_souborovy_vyskyt_termu))
                     .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                     .getSingleResult());
        final URI newTermUri = Generator.generateUri();
        occurrence.setTerm(newTermUri);
        transactional(() -> sut.update(occurrence));
        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?x a ?occurrence ; ?hasTerm ?term .} }", Boolean.class)
                     .setParameter("g", TermOccurrence.resolveContext(file.getUri()))
                     .setParameter("x", occurrence.getUri())
                     .setParameter("occurrence", URI.create(Vocabulary.s_c_souborovy_vyskyt_termu))
                     .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                     .getSingleResult());
    }
}
