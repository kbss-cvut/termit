package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.descriptors.FieldDescriptor;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

public class TermDaoWorkspacesTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private WorkspaceMetadataProvider wsMetadataCache;

    @Autowired
    private TermDao sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        saveVocabulary(vocabulary);
    }

    private void saveVocabulary(Vocabulary vocabulary) {
        final WorkspaceMetadata wsMetadata = wsMetadataCache.getCurrentWorkspaceMetadata();
        doReturn(new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), vocabulary.getUri())).when(wsMetadata)
                                                                                                   .getVocabularyInfo(
                                                                                                           vocabulary
                                                                                                                   .getUri());
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                conn.begin();
                conn.add(Generator
                        .generateWorkspaceReferences(Collections.singleton(vocabulary), wsMetadata.getWorkspace()));
                conn.commit();
            }
        });
    }

    @Test
    void findAllReturnsTermsFromVocabularyInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(1, result.size());
        assertEquals(term.getLabel(), result.get(0).getLabel());
    }

    private URI addTermToVocabularyInAnotherWorkspace(Term term) {
        final Vocabulary anotherWorkspaceVocabulary = Generator.generateVocabulary();
        anotherWorkspaceVocabulary.setUri(vocabulary.getUri());
        final URI anotherWorkspaceCtx = Generator.generateUri();
        final Term copy = new Term();
        copy.setUri(term.getUri());
        copy.setLabel("Different label");

        transactional(() -> {
            em.persist(anotherWorkspaceVocabulary, new EntityDescriptor(anotherWorkspaceCtx));
            copy.setGlossary(term.getGlossary());
            final EntityDescriptor termDescriptor = new EntityDescriptor(anotherWorkspaceCtx);
            termDescriptor.addAttributeContext(descriptorFactory.fieldSpec(Term.class, "parentTerms"), null);
            termDescriptor.addAttributeDescriptor(descriptorFactory.fieldSpec(Term.class, "vocabulary"),
                    new FieldDescriptor((URI) null, descriptorFactory.fieldSpec(Term.class, "vocabulary")));
            em.persist(copy, termDescriptor);
        });
        return anotherWorkspaceCtx;
    }

    @Test
    void findAllRootsReturnsTermsFromVocabularyInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(term.getLabel(), result.get(0).getLabel());
    }

    @Test
    void subTermLoadingRetrievesSubTermsDeclaredInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.addParentTerm(term);
        child.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(child, vocabulary.getUri(), em);
            insertNarrowerStatements(child,
                    wsMetadataCache.getCurrentWorkspaceMetadata().getVocabularyInfo(vocabulary.getUri()).getContext());
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        final Term resultParent = result.get(0);
        assertEquals(1, resultParent.getSubTerms().size());
        assertEquals(child.getUri(), resultParent.getSubTerms().iterator().next().getUri());
    }

    /**
     * Simulate the inverse of skos:broader and skos:narrower
     *
     * @param child Term whose parents need skos:narrower relationships to them
     */
    private void insertNarrowerStatements(Term child, URI context) {
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            final IRI narrower = vf.createIRI(SKOS.NARROWER);
            for (Term parent : child.getParentTerms()) {
                conn.add(vf.createStatement(vf.createIRI(parent.getUri().toString()), narrower,
                        vf.createIRI(child.getUri().toString()), vf.createIRI(context.toString())));
            }
            conn.commit();
        }
    }

    @Test
    void subTermLoadingDoesNotRetrieveSubTermsDeclaredInDifferentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.addParentTerm(term);
        child.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        final URI ctx = addTermToVocabularyInAnotherWorkspace(term);
        transactional(() -> {
            final EntityDescriptor termDescriptor = new EntityDescriptor(ctx);
            termDescriptor.addAttributeContext(descriptorFactory.fieldSpec(Term.class, "parentTerms"), null);
            termDescriptor.addAttributeDescriptor(descriptorFactory.fieldSpec(Term.class, "vocabulary"),
                    new FieldDescriptor((URI) null, descriptorFactory.fieldSpec(Term.class, "vocabulary")));
            em.persist(child, termDescriptor);
            insertNarrowerStatements(child, ctx);
            Generator.addTermInVocabularyRelationship(child, vocabulary.getUri(), em);
        });

        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        final Term resultParent = result.get(0);
        assertThat(resultParent.getSubTerms(), anyOf(nullValue(), empty()));
    }

    @Test
    void findRetrievesTermInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term.getLabel(), result.get().getLabel());
    }

    @Test
    void findAllBySearchStringRetrievesTermsInCurrentVocabulary() {
        final Term term = Generator.generateTermWithId();
        term.setLabel("searched label");
        term.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<Term> result = sut.findAll("searched", vocabulary);
        assertEquals(1, result.size());
        assertEquals(term.getLabel(), result.get(0).getLabel());
    }

    @Test
    void findAllRootsRetrievesRootTermsFromVocabulariesInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Vocabulary importedVocabulary = Generator.generateVocabularyWithId();
        saveVocabulary(importedVocabulary);
        final Term importedTerm = Generator.generateTermWithId();
        importedTerm.setGlossary(importedVocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            vocabulary.getGlossary().addRootTerm(term);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            em.persist(importedTerm, descriptorFactory.termDescriptor(importedVocabulary));
            Generator.addTermInVocabularyRelationship(importedTerm, importedVocabulary.getUri(), em);
            vocabulary.setImportedVocabularies(Collections.singleton(importedVocabulary.getUri()));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            importedVocabulary.getGlossary().addRootTerm(importedTerm);
            em.merge(importedVocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(importedVocabulary));
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<Term> result =
                sut.findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(2, result.size());
        assertThat(result, hasItem(term));
        assertThat(result, hasItem(importedTerm));
        assertEquals(term.getLabel(), result.get(result.indexOf(term)).getLabel());
    }

    @Test
    void findAllIncludingImportsBySearchStringRetrievesTermsFromVocabulariesInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setLabel("searched string one");
        final Vocabulary importedVocabulary = Generator.generateVocabularyWithId();
        saveVocabulary(importedVocabulary);
        final Term importedTerm = Generator.generateTermWithId();
        importedTerm.setGlossary(importedVocabulary.getGlossary().getUri());
        importedTerm.setLabel("searched string two");
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            vocabulary.getGlossary().addRootTerm(term);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            em.persist(importedTerm, descriptorFactory.termDescriptor(importedVocabulary));
            Generator.addTermInVocabularyRelationship(importedTerm, importedVocabulary.getUri(), em);
            vocabulary.setImportedVocabularies(Collections.singleton(importedVocabulary.getUri()));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            importedVocabulary.getGlossary().addRootTerm(importedTerm);
            em.merge(importedVocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(importedVocabulary));
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final List<Term> result = sut.findAllIncludingImported("searched", vocabulary);
        assertEquals(2, result.size());
        assertThat(result, hasItem(term));
        assertThat(result, hasItem(importedTerm));
        assertEquals(term.getLabel(), result.get(result.indexOf(term)).getLabel());
        assertEquals(importedTerm.getLabel(), result.get(result.indexOf(importedTerm)).getLabel());
    }
}
