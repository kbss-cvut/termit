package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TermDaoWorkspaceTest extends BaseTermDaoTestRunner {

    @Autowired
    private EditableVocabularies editableVocabularies;

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void findCorrectlyResolvesDescriptorContextsForTermInWorkspace() {
        final URI workingCtx = Generator.generateUri();
        final Term canonical = Generator.generateTermWithId(vocabulary.getUri());
        canonical.setGlossary(vocabulary.getGlossary().getUri());
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term workingCopy = cloneTerm(canonical);
        workingCopy.getLabel().set(Environment.LANGUAGE, "Different label");
        workingCopy.setGlossary(workingVoc.getGlossary().getUri());
        final Term child = Generator.generateTermWithId(vocabulary.getUri());
        child.setGlossary(workingVoc.getGlossary().getUri());
        transactional(() -> {
            em.persist(canonical, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(canonical, vocabulary.getUri(), em);
        });
        em.clear();
        transactional(() -> {
            child.addParentTerm(workingCopy);
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(workingCopy, descriptorFactory.termDescriptor(workingCtx));
            em.persist(child, descriptorFactory.termDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
        });

        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        final Optional<Term> result = sut.find(child.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getParentTerms(), hasItem(workingCopy));
    }

    private void insertContextBasedOnCanonical(URI workingCtx, URI canonicalCtx) {
        Environment.insertContextBasedOnCanonical(workingCtx, canonicalCtx, em);
    }

    private Term cloneTerm(Term original) {
        final Term clone = new Term(original.getUri());
        clone.setLabel(new MultilingualString(original.getLabel().getValue()));
        clone.setDefinition(new MultilingualString(original.getDefinition().getValue()));
        clone.setDescription(new MultilingualString(original.getDescription().getValue()));
        clone.setSources(new HashSet<>(Utils.emptyIfNull(original.getSources())));
        clone.setVocabulary(original.getVocabulary());
        return clone;
    }

    @Test
    void findHandlesChangeToSkosRelationshipsAttributeInWorkspace() {
        final URI workingCtx = Generator.generateUri();
        final Term canonical = Generator.generateTermWithId(vocabulary.getUri());
        canonical.setGlossary(vocabulary.getGlossary().getUri());
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term workingCopy = cloneTerm(canonical);
        workingCopy.setGlossary(workingVoc.getGlossary().getUri());
        final Vocabulary relatedVocabulary = Generator.generateVocabularyWithId();
        final Term related = Generator.generateTermWithId(relatedVocabulary.getUri());
        transactional(() -> {
            em.persist(canonical, descriptorFactory.termDescriptor(vocabulary));
            em.persist(relatedVocabulary, descriptorFactory.vocabularyDescriptor(relatedVocabulary));
            related.setGlossary(relatedVocabulary.getGlossary().getUri());
            em.persist(related, descriptorFactory.termDescriptor(relatedVocabulary));
            Generator.addTermInVocabularyRelationship(canonical, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(related, relatedVocabulary.getUri(), em);
        });
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(workingCopy, descriptorFactory.termDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                conn.add(vf.createIRI(workingCopy.getUri().toString()), vf.createIRI(SKOS.EXACT_MATCH),
                         vf.createIRI(related.getUri().toString()), vf.createIRI(workingCtx.toString()));
            }
        });
        em.getEntityManagerFactory().getCache().evictAll();

        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        final Optional<Term> result = sut.find(workingCopy.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getExactMatchTerms(), hasItem(new TermInfo(related)));
    }

    @Test
    void updateHandlesAddingReferenceToTermFromDifferentVocabulary() {
        final URI workingCtx = Generator.generateUri();
        final Term canonical = Generator.generateTermWithId(vocabulary.getUri());
        canonical.setGlossary(vocabulary.getGlossary().getUri());
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term workingCopy = cloneTerm(canonical);
        workingCopy.setGlossary(workingVoc.getGlossary().getUri());
        final Vocabulary relatedVocabulary = Generator.generateVocabularyWithId();
        final Term related = Generator.generateTermWithId(relatedVocabulary.getUri());
        transactional(() -> {
            em.persist(canonical, descriptorFactory.termDescriptor(vocabulary));
            em.persist(relatedVocabulary, descriptorFactory.vocabularyDescriptor(relatedVocabulary));
            related.setGlossary(relatedVocabulary.getGlossary().getUri());
            em.persist(related, descriptorFactory.termDescriptor(relatedVocabulary));
            Generator.addTermInVocabularyRelationship(canonical, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(related, relatedVocabulary.getUri(), em);
        });
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(workingCopy, descriptorFactory.termDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
        });
        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        workingCopy.addRelatedMatchTerm(new TermInfo(related));

        transactional(() -> sut.update(workingCopy));
        final Optional<Term> result = sut.find(workingCopy.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getRelatedMatch(), hasItem(new TermInfo(related)));
    }

    @Test
    void persistHandlesReferenceToTermFromDifferentVocabulary() {
        final URI workingCtx = Generator.generateUri();
        final Term toPersist = Generator.generateTermWithId();
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Vocabulary relatedVocabulary = Generator.generateVocabularyWithId();
        final Term related = Generator.generateTermWithId(relatedVocabulary.getUri());
        transactional(() -> {
            em.persist(relatedVocabulary, descriptorFactory.vocabularyDescriptor(relatedVocabulary));
            related.setGlossary(relatedVocabulary.getGlossary().getUri());
            em.persist(related, descriptorFactory.termDescriptor(relatedVocabulary));
            Generator.addTermInVocabularyRelationship(related, relatedVocabulary.getUri(), em);
        });
        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
        });
        toPersist.addRelatedMatchTerm(new TermInfo(related));

        transactional(() -> {
            sut.persist(toPersist, workingVoc);
            // Simulate inference
            Generator.addTermInVocabularyRelationship(toPersist, workingVoc.getUri(), em);
        });
        final Optional<Term> result = sut.find(toPersist.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getRelatedMatch(), hasItem(new TermInfo(related)));
    }

    @Test
    void findAllIncludingImportedLoadsTermsFromImportedVocabulariesAsWell() {
        final Term canonicalOne = Generator.generateTermWithId(vocabulary.getUri());
        canonicalOne.setGlossary(vocabulary.getGlossary().getUri());
        final Vocabulary vocabularyTwo = Generator.generateVocabularyWithId();
        vocabularyTwo.setImportedVocabularies(Collections.singleton(vocabulary.getUri()));
        final Term canonicalTwo = Generator.generateTermWithId(vocabularyTwo.getUri());
        transactional(() -> {
            em.persist(canonicalOne, descriptorFactory.termDescriptorForSave(vocabulary.getUri()));
            em.persist(vocabularyTwo, descriptorFactory.vocabularyDescriptor(vocabularyTwo));
            canonicalTwo.setGlossary(vocabularyTwo.getGlossary().getUri());
            em.persist(canonicalTwo, descriptorFactory.termDescriptorForSave(vocabularyTwo.getUri()));
            Generator.addTermInVocabularyRelationship(canonicalOne, vocabulary.getUri(), em);
        });
        final URI workingCtxOne = Generator.generateUri();
        final Vocabulary workingVocOne = Environment.cloneVocabulary(vocabulary);
        final Term workingCopyOne = cloneTerm(canonicalOne);
        workingCopyOne.getLabel().set(Environment.LANGUAGE, "Different label");
        workingCopyOne.setGlossary(vocabulary.getGlossary().getUri());
        final URI workingCtxTwo = Generator.generateUri();
        final Vocabulary workingVocTwo = Environment.cloneVocabulary(vocabularyTwo);
        final Term workingCopyTwo = cloneTerm(canonicalTwo);
        transactional(() -> {
            em.persist(workingVocOne, descriptorFactory.vocabularyDescriptor(workingCtxOne));
            em.persist(workingCopyOne, descriptorFactory.termDescriptorForSave(workingCtxOne));
            em.persist(workingVocTwo, descriptorFactory.termDescriptor(workingCtxTwo));
            em.persist(workingCopyTwo, descriptorFactory.termDescriptorForSave(workingCtxTwo));
            insertContextBasedOnCanonical(workingCtxOne, vocabulary.getUri());
            insertContextBasedOnCanonical(workingCtxTwo, vocabularyTwo.getUri());
        });
        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtxOne);
        editableVocabularies.registerEditableVocabulary(vocabularyTwo.getUri(), workingCtxTwo);

        final List<TermDto> result = sut.findAllIncludingImported(workingVocTwo);
        assertThat(result, hasItems(new TermDto(workingCopyTwo), new TermDto(workingCopyOne)));
    }

    @Test
    void findAllRootsWithoutVocabularyResolvesVocabularyContextsForRelevantTermsPage() {
        final URI workingCtx = Generator.generateUri();
        final Term canonical = Generator.generateTermWithId(vocabulary.getUri());
        canonical.setGlossary(vocabulary.getGlossary().getUri());
        vocabulary.getGlossary().addRootTerm(canonical);
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term workingCopy = cloneTerm(canonical);
        workingCopy.setGlossary(workingVoc.getGlossary().getUri());
        workingCopy.setLabel(MultilingualString.create("Updated label", Environment.LANGUAGE));
        workingVoc.getGlossary().addRootTerm(workingCopy);
        final Vocabulary relatedVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> {
            em.persist(canonical, descriptorFactory.termDescriptor(vocabulary));
            em.persist(relatedVocabulary, descriptorFactory.vocabularyDescriptor(relatedVocabulary));
            Generator.addTermInVocabularyRelationship(canonical, vocabulary.getUri(), em);
        });
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(workingCopy, descriptorFactory.termDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
        });

        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        final List<TermDto> result = sut.findAllRoots(Constants.DEFAULT_PAGE_SPEC, Collections.emptySet());
        assertThat(result, hasItem(new TermDto(workingCopy)));
    }

    @Test
    void findAllBySearchStringDoesNotReturnNewTermCreatedInAnotherWorkspace() {
        final URI workingCtx = Generator.generateUri();
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final URI anotherWorkingCtx = Generator.generateUri();
        final Vocabulary anotherWorkingVoc = Environment.cloneVocabulary(anotherVocabulary);
        final Term newTerm = Generator.generateTermWithId(anotherVocabulary.getUri());
        newTerm.setGlossary(workingVoc.getGlossary().getUri());
        workingVoc.getGlossary().addRootTerm(newTerm);
        transactional(() -> em.persist(anotherVocabulary, descriptorFactory.vocabularyDescriptor(anotherVocabulary)));
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(anotherWorkingVoc, descriptorFactory.vocabularyDescriptor(anotherWorkingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
            insertContextBasedOnCanonical(anotherWorkingCtx, anotherVocabulary.getUri());
            em.persist(newTerm, descriptorFactory.termDescriptor(anotherWorkingCtx));
            Generator.addTermInVocabularyRelationship(newTerm, anotherVocabulary.getUri(), em);
        });

        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        final List<TermDto> result = sut.findAll(
                newTerm.getPrimaryLabel().substring(0, newTerm.getPrimaryLabel().length() / 2));
        assertThat(result, not(hasItem(new TermDto(newTerm))));
    }

    @Test
    void findAllBySearchStringReturnsNewTermCreatedInCurrentWorkspace() {
        final URI workingCtx = Generator.generateUri();
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(workingVoc.getGlossary().getUri());
        workingVoc.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
            em.persist(term, descriptorFactory.termDescriptor(workingCtx));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        final List<TermDto> result = sut.findAll(
                term.getPrimaryLabel().substring(0, term.getPrimaryLabel().length() / 2));
        assertThat(result, hasItem(new TermDto(term)));
    }

    @Test
    void setTermStatusSetsItOnlyInCurrentWorkspace() {
        final URI workingCtx = Generator.generateUri();
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term canonical = Generator.generateTermWithId(vocabulary.getUri());
        canonical.setDraft(true);
        canonical.setGlossary(vocabulary.getGlossary().getUri());
        final Term workingCopy = cloneTerm(canonical);
        workingCopy.setGlossary(workingVoc.getGlossary().getUri());
        workingVoc.getGlossary().addRootTerm(canonical);
        transactional(() -> {
            em.persist(canonical, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(canonical, vocabulary.getUri(), em);
        });
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(workingCopy, descriptorFactory.termDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
        });

        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        transactional(() -> sut.setAsConfirmed(workingCopy));

        final String query = "SELECT ?status WHERE { GRAPH ?g { ?t ?isDraft ?status } }";
        assertFalse(em.createNativeQuery(query, Boolean.class)
                      .setParameter("g", workingCtx)
                      .setParameter("t", canonical)
                      .setParameter("isDraft", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_draft))
                      .getSingleResult());
        assertTrue(em.createNativeQuery(query, Boolean.class)
                     .setParameter("g", vocabulary.getUri())
                     .setParameter("t", canonical)
                     .setParameter("isDraft", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_draft))
                     .getSingleResult());
    }

    @Test
    void inverseRelationshipLoadingOfCanonicalVersionSkipsReferencesExistingOnlyInWorkspace() {
        final URI workingCtx = Generator.generateUri();
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term canonical = Generator.generateTermWithId(vocabulary.getUri());
        canonical.setDraft(true);
        canonical.setGlossary(vocabulary.getGlossary().getUri());
        final Term workingCopy = cloneTerm(canonical);
        workingCopy.setGlossary(workingVoc.getGlossary().getUri());
        workingVoc.getGlossary().addRootTerm(canonical);
        final URI otherWorkingCtx = Generator.generateUri();
        final Vocabulary otherVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary otherVocabularyWorking = Environment.cloneVocabulary(otherVocabulary);
        final Term canonicalChild = Generator.generateTermWithId(otherVocabulary.getUri());
        final Term workingChild = cloneTerm(canonicalChild);
        workingChild.addParentTerm(workingCopy);
        transactional(() -> {
            em.persist(canonical, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(canonical, vocabulary.getUri(), em);
            em.persist(otherVocabulary, descriptorFactory.vocabularyDescriptor(otherVocabulary.getUri()));
            em.persist(canonicalChild, descriptorFactory.termDtoDescriptor(otherVocabulary.getUri()));
            Generator.addTermInVocabularyRelationship(canonicalChild, otherVocabulary.getUri(), em);
        });
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(workingCopy, descriptorFactory.termDescriptor(workingCtx));
            insertContextBasedOnCanonical(workingCtx, vocabulary.getUri());
            em.persist(otherVocabularyWorking, descriptorFactory.vocabularyDescriptor(otherWorkingCtx));
            insertContextBasedOnCanonical(otherWorkingCtx, otherVocabulary.getUri());
            em.persist(workingChild, descriptorFactory.termDescriptor(otherWorkingCtx));
        });
        em.getEntityManagerFactory().getCache().evictAll();

        final Optional<Term> result = sut.find(canonical.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getSubTerms(), not(hasItem(new TermInfo(canonicalChild))));
    }
}
