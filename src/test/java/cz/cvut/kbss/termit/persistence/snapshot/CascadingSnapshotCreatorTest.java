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
package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

class CascadingSnapshotCreatorTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private CascadingSnapshotCreator sut;

    private User author;

    private final Map<Vocabulary, Term> vocabularyTerms = new HashMap<>();

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void createSnapshotRecursivelyIdentifiesVocabulariesToSnapshotViaTermSkosRelationships() {
        final Vocabulary root = generateVocabularyWithTerm(false);
        Vocabulary last = root;
        for (int i = 0; i < 5; i++) {
            final Vocabulary another = generateVocabularyWithTerm(false);
            addSkosRelationship(vocabularyTerms.get(last), vocabularyTerms.get(another));
            last = another;
        }

        transactional(() -> sut.createSnapshot(root));
        vocabularyTerms.keySet().forEach(
                v -> assertTrue(em.createNativeQuery("ASK { ?snapshot ?isSnapshotOf ?v . }", Boolean.class)
                                  .setParameter("isSnapshotOf",
                                          URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku))
                                  .setParameter("v", v)
                                  .getSingleResult()));
    }

    private Vocabulary generateVocabularyWithTerm(boolean rootTerm) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            if (rootTerm) {
                vocabulary.getGlossary().addRootTerm(term);
            }
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        vocabularyTerms.put(vocabulary, term);
        return vocabulary;
    }

    private void addSkosRelationship(Term termOne, Term termTwo) {
        final int i = Generator.randomInt(0, 3);
        switch (i) {
            case 0:
                termOne.addExactMatch(new TermInfo(termTwo));
                break;
            case 1:
                termOne.addRelatedMatchTerm(new TermInfo(termTwo));
                break;
            case 2:
                termOne.addParentTerm(termTwo);
                break;
        }
        transactional(() -> em.merge(termOne, descriptorFactory.termDescriptor(termOne)));
    }

    @Test
    void createSnapshotCreatesSnapshotOfVocabulary() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(false);

        transactional(() -> {
            final Snapshot result = sut.createSnapshot(vocabulary);
            assertNotNull(result);
            assertEquals(vocabulary.getUri(), result.getVersionOf());
            assertThat(result.getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku));
        });
        final Vocabulary result = findRequiredSnapshot(vocabulary, Vocabulary.class);
        assertEquals(vocabulary.getLabel(), result.getLabel());
        assertEquals(vocabulary.getDescription(), result.getDescription());
        assertNotNull(result.getGlossary());
        assertNotNull(result.getModel());
    }

    @Test
    void createSnapshotEnsuresRootTermsAreAccessibleFromGlossarySnapshot() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(true);

        transactional(() -> sut.createSnapshot(vocabulary));
        final Vocabulary result = findRequiredSnapshot(vocabulary, Vocabulary.class);
        assertEquals(1, result.getGlossary().getRootTerms().size());
    }

    @Test
    void createSnapshotCreatesVocabularyTermSnapshots() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(true);
        final Term term = vocabularyTerms.get(vocabulary);

        transactional(() -> sut.createSnapshot(vocabulary));
        final Vocabulary vocabularyResult = findRequiredSnapshot(vocabulary, Vocabulary.class);
        final Term result = findRequiredSnapshot(term, Term.class);
        assertEquals(term.getLabel(), result.getLabel());
        assertEquals(term.getDefinition(), result.getDefinition());
        assertEquals(term.getDescription(), result.getDescription());
        assertEquals(vocabularyResult.getGlossary().getUri(), result.getGlossary());
        assertThat(result.getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu));
    }

    private <T extends Asset<?>> T findRequiredSnapshot(HasIdentifier asset, Class<T> cls) {
        final String isSnapshotOf = Vocabulary.class.equals(cls) ? cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku : cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_pojmu;
        final T result = em.createNativeQuery("SELECT ?s WHERE { ?s ?isSnapshotOf ?a }", cls)
                           .setParameter("isSnapshotOf", URI.create(isSnapshotOf))
                           .setParameter("a", asset)
                           .getSingleResult();
        assertNotNull(result);
        return result;
    }

    @Test
    void createSnapshotEnsuresSnapshotsAreConnectedToAssetOriginals() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(true);
        final Term term = vocabularyTerms.get(vocabulary);

        transactional(() -> sut.createSnapshot(vocabulary));
        final String queryString = "ASK { ?s ?isSnapshotOf ?a }";
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                             cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku)).setParameter("a", vocabulary)
                     .getSingleResult());
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                             cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_glosare)).setParameter("a", vocabulary.getGlossary())
                     .getSingleResult());
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                             cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_modelu)).setParameter("a", vocabulary.getModel())
                     .getSingleResult());
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_pojmu)).setParameter("a", term).getSingleResult());
    }

    @Test
    void createSnapshotCreatesSnapshotsOfInterconnectedVocabulariesAndConnectsTheirTerms() {
        final Vocabulary root = generateVocabularyWithTerm(false);
        Vocabulary last = root;
        for (int i = 0; i < 5; i++) {
            final Vocabulary another = generateVocabularyWithTerm(false);
            addSkosRelationship(vocabularyTerms.get(last), vocabularyTerms.get(another));
            last = another;
        }

        transactional(() -> sut.createSnapshot(root));
        for (Term term : vocabularyTerms.values()) {
            final Term snapshot = findRequiredSnapshot(term, Term.class);
            if (term.getExactMatchTerms() != null) {
                final TermInfo exactMatch = term.getExactMatchTerms().iterator().next();
                final Term exactMatchSnapshot = findRequiredSnapshot(exactMatch, Term.class);
                assertThat(snapshot.getExactMatchTerms(), hasItem(new TermInfo(exactMatchSnapshot)));
            } else if (term.getRelatedMatch() != null) {
                final TermInfo relatedMatch = term.getRelatedMatch().iterator().next();
                final Term relatedMatchSnapshot = findRequiredSnapshot(relatedMatch, Term.class);
                assertThat(snapshot.getRelatedMatch(), hasItem(new TermInfo(relatedMatchSnapshot)));
            } else if (term.getExternalParentTerms() != null) {
                final Term parent = term.getExternalParentTerms().iterator().next();
                final Term parentSnapshot = findRequiredSnapshot(parent, Term.class);
                assertThat(snapshot.getExternalParentTerms(), hasItem(parentSnapshot));
            }
        }
    }

    @Test
    void createSnapshotSkipsDocument() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(true);
        final Document doc = Generator.generateDocumentWithId();
        vocabulary.setDocument(doc);
        transactional(() -> {
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(doc, descriptorFactory.documentDescriptor(vocabulary));
        });

        transactional(() -> sut.createSnapshot(vocabulary));
        final Vocabulary result = findRequiredSnapshot(vocabulary, Vocabulary.class);
        assertNull(result.getDocument());
    }

    @Test
    void createSnapshotIncludesExplicitlyImportedVocabularies() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(false);
        final Vocabulary imported = generateVocabularyWithTerm(false);
        transactional(() -> {
            vocabulary.setImportedVocabularies(Collections.singleton(imported.getUri()));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        transactional(() -> sut.createSnapshot(vocabulary));
        final Vocabulary snapshot = findRequiredSnapshot(vocabulary, Vocabulary.class);
        final Vocabulary importedSnapshot = findRequiredSnapshot(imported, Vocabulary.class);
        assertThat(snapshot.getImportedVocabularies(), hasItem(importedSnapshot.getUri()));
    }
}
