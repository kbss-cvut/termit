package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.HashSet;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
        final Vocabulary workingVoc = cloneVocabulary(vocabulary);
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
        });

        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        final Optional<Term> result = sut.find(child.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getParentTerms(), hasItem(workingCopy));
    }

    private Vocabulary cloneVocabulary(Vocabulary original) {
        final Vocabulary clone = Generator.generateVocabulary();
        clone.setUri(original.getUri());
        clone.setLabel(original.getLabel());
        clone.getGlossary().setUri(original.getGlossary().getUri());
        clone.getModel().setUri(original.getModel().getUri());
        return clone;
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
        final Vocabulary workingVoc = cloneVocabulary(vocabulary);
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
        final Vocabulary workingVoc = cloneVocabulary(vocabulary);
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
        final Vocabulary workingVoc = cloneVocabulary(vocabulary);
        final Vocabulary relatedVocabulary = Generator.generateVocabularyWithId();
        final Term related = Generator.generateTermWithId(relatedVocabulary.getUri());
        transactional(() -> {
            em.persist(relatedVocabulary, descriptorFactory.vocabularyDescriptor(relatedVocabulary));
            related.setGlossary(relatedVocabulary.getGlossary().getUri());
            em.persist(related, descriptorFactory.termDescriptor(relatedVocabulary));
            Generator.addTermInVocabularyRelationship(related, relatedVocabulary.getUri(), em);
        });
        editableVocabularies.registerEditableVocabulary(vocabulary.getUri(), workingCtx);
        transactional(() -> em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx)));
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
}
