package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.util.DescriptorFactory;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TermDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermDao sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        transactional(() -> em.persist(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary)));
    }

    @Test
    void findAllRootsWithDefaultPageSpecReturnsAllTerms() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
    }

    private void addTermsAndSave(Collection<Term> terms, Vocabulary vocabulary) {
        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            em.merge(vocabulary.getGlossary(), DescriptorFactory.glossaryDescriptor(vocabulary));
            terms.forEach(t -> {
                t.setGlossary(vocabulary.getGlossary().getUri());
                em.persist(t, DescriptorFactory.termDescriptor(vocabulary));
                addTermInVocabularyRelationship(t, vocabulary.getUri());
            });
        });
    }

    private List<Term> generateTerms(int count) {
        return IntStream.range(0, count).mapToObj(i -> Generator.generateTermWithId())
                        .sorted(Comparator.comparing(Term::getLabel)).collect(Collectors.toList());
    }

    private void addTermInVocabularyRelationship(Term term, URI vocabularyIri) {
        Generator.addTermInVocabularyRelationship(term, vocabularyIri, em);
    }

    @Test
    void findAllRootsReturnsMatchingPageWithTerms() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        // Paging starts at 0
        final List<Term> result = sut
                .findAllRoots(vocabulary, PageRequest.of(1, terms.size() / 2), Collections.emptyList());
        final List<Term> subList = terms.subList(terms.size() / 2, terms.size());
        assertEquals(subList, result);
    }

    @Test
    void findAllRootsReturnsOnlyTermsInSpecifiedVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Vocabulary another = Generator.generateVocabulary();
        another.setUri(Generator.generateUri());
        another.getGlossary().setRootTerms(generateTerms(4).stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> em.persist(another));

        final List<Term> result = sut
                .findAllRoots(vocabulary, PageRequest.of(0, terms.size() / 2), Collections.emptyList());
        assertEquals(terms.size() / 2, result.size());
        assertTrue(terms.containsAll(result));
    }

    @Test
    void findAllRootsReturnsOnlyRootTerms() {
        final List<Term> rootTerms = generateTerms(10);
        addTermsAndSave(new HashSet<>(rootTerms), vocabulary);
        transactional(() -> rootTerms.forEach(t -> {
            final Term child = Generator.generateTermWithId(vocabulary.getUri());
            child.setParentTerms(Collections.singleton(t));
            em.persist(child, DescriptorFactory.termDescriptor(vocabulary));
        }));


        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(rootTerms, result);
    }

    @Test
    void findAllBySearchStringReturnsTermsWithMatchingLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<Term> result = sut.findAll(terms.get(0).getLabel(), vocabulary);
        assertEquals(1, result.size());
        assertTrue(terms.contains(result.get(0)));
    }

    @Test
    void findAllBySearchStringReturnsTermsWithMatchingLabelWhichAreNotRoots() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Term root = terms.get(Generator.randomIndex(terms));
        final Term child = Generator.generateTermWithId(vocabulary.getUri());
        child.setLabel("test");
        child.setParentTerms(Collections.singleton(root));
        child.setGlossary(vocabulary.getGlossary().getUri());
        final Term matchingDesc = Generator.generateTermWithId();
        matchingDesc.setLabel("Metropolitan plan");
        matchingDesc.setParentTerms(Collections.singleton(child));
        transactional(() -> {
            em.persist(child, DescriptorFactory.termDescriptor(vocabulary));
            em.persist(matchingDesc, DescriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(child, vocabulary.getUri());
            addTermInVocabularyRelationship(matchingDesc, vocabulary.getUri());
            insertNarrowerStatements(matchingDesc, child);
        });

        final List<Term> result = sut.findAll("plan", vocabulary);
        assertEquals(1, result.size());
        assertEquals(matchingDesc, result.get(0));
    }

    /**
     * Simulate the inverse of skos:broader and skos:narrower
     *
     * @param children Terms whose parents need skos:narrower relationships to them
     */
    private void insertNarrowerStatements(Term... children) {
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            final IRI narrower = vf.createIRI(SKOS.NARROWER);
            for (Term t : children) {
                for (Term parent : t.getParentTerms()) {
                    conn.add(vf.createStatement(vf.createIRI(parent.getUri().toString()), narrower,
                            vf.createIRI(t.getUri().toString()), vf.createIRI(vocabulary.getUri().toString())));
                }
            }
            conn.commit();
        }
    }

    @Test
    void existsInVocabularyReturnsTrueForLabelExistingInVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final String label = terms.get(0).getLabel();
        assertTrue(sut.existsInVocabulary(label, vocabulary));
    }

    @Test
    void existsInVocabularyReturnsFalseForUnknownLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        assertFalse(sut.existsInVocabulary("unknown label", vocabulary));
    }

    @Test
    void existsInVocabularyReturnsTrueWhenLabelDiffersOnlyInCase() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final String label = terms.get(0).getLabel().toLowerCase();
        assertTrue(sut.existsInVocabulary(label, vocabulary));
    }

    @Test
    void findAllGetsAllTermsInVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(terms.size(), result.size());
        assertTrue(terms.containsAll(result));
    }

    @Test
    void isEmptyReturnsTrueForEmptyVocabulary() {
        assertTrue(sut.isEmpty(vocabulary));
    }

    @Test
    void isEmptyReturnsFalseForNonemptyVocabulary() {
        final List<Term> terms = generateTerms(1);
        addTermsAndSave(terms, vocabulary);
        assertFalse(sut.isEmpty(vocabulary));
    }

    @Test
    void findAllReturnsAllTermsFromVocabularyOrderedByLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final List<Term> result = sut.findAll(vocabulary);
        terms.sort(Comparator.comparing(Term::getLabel));
        assertEquals(terms, result);
    }

    @Test
    void findAllIncludingImportedReturnsTermsInVocabularyAndImportedVocabularies() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);
        final Vocabulary parent = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Collections.singleton(parent.getUri()));
        transactional(() -> {
            em.merge(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parent, DescriptorFactory.vocabularyDescriptor(parent));
        });
        final List<Term> parentTerms = generateTerms(3);
        addTermsAndSave(parentTerms, parent);

        final List<Term> result = sut.findAllIncludingImported(vocabulary);
        final List<Term> allExpected = new ArrayList<>(terms);
        allExpected.addAll(parentTerms);
        allExpected.sort(Comparator.comparing(Asset::getLabel));
        assertEquals(allExpected, result);
    }

    @Test
    void persistSavesTermIntoVocabularyContext() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri(), DescriptorFactory.termDescriptor(vocabulary));
        assertNotNull(result);
        assertEquals(term, result);
    }

    @Test
    void updateUpdatesTermInVocabularyContext() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.merge(vocabulary.getGlossary(), DescriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, DescriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
        });

        final String updatedLabel = "Updated label";
        final String oldLabel = term.getLabel();
        term.setLabel(updatedLabel);
        em.getEntityManagerFactory().getCache().evictAll();
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri(), DescriptorFactory.termDescriptor(vocabulary));
        assertEquals(updatedLabel, result.getLabel());
        assertFalse(em.createNativeQuery("ASK WHERE { ?x ?hasLabel ?label }", Boolean.class)
                      .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))
                      .setParameter("label", oldLabel, Constants.DEFAULT_LANGUAGE).getSingleResult());
    }

    @Test
    void findAllRootsReturnsOnlyTermsWithMatchingLabelLanguage() {
        final List<Term> terms = generateTerms(5);
        final Term foreignLabelTerm = Generator.generateTermWithId();
        final List<Term> allTerms = new ArrayList<>(terms);
        allTerms.add(foreignLabelTerm);
        addTermsAndSave(allTerms, vocabulary);
        transactional(() -> insertForeignLabel(foreignLabelTerm));

        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
    }

    private void insertForeignLabel(Term term) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.remove(vf.createIRI(term.getUri().toString()), vf.createIRI(SKOS.PREF_LABEL), null);
            conn.add(vf.createIRI(term.getUri().toString()), vf.createIRI(SKOS.PREF_LABEL),
                    vf.createLiteral("Adios", "es"));
        }
    }

    @Test
    void findAllReturnsOnlyTermsWithMatchingLanguageLabel() {
        final List<Term> terms = generateTerms(5);
        final Term foreignLabelTerm = Generator.generateTermWithId();
        final List<Term> allTerms = new ArrayList<>(terms);
        allTerms.add(foreignLabelTerm);
        addTermsAndSave(allTerms, vocabulary);
        transactional(() -> insertForeignLabel(foreignLabelTerm));

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(terms, result);
    }

    @Test
    void findAllRootsIncludingImportsGetsRootTermsFromVocabularyImportChain() {
        final List<Term> directTerms = generateTerms(3);
        addTermsAndSave(directTerms, vocabulary);
        final Vocabulary parent = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Collections.singleton(parent.getUri()));
        final Vocabulary grandParent = Generator.generateVocabularyWithId();
        parent.setImportedVocabularies(Collections.singleton(grandParent.getUri()));
        transactional(() -> {
            em.merge(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parent, DescriptorFactory.vocabularyDescriptor(parent));
            em.persist(grandParent, DescriptorFactory.vocabularyDescriptor(grandParent));
        });
        final List<Term> parentTerms = generateTerms(3);
        addTermsAndSave(parentTerms, parent);
        final List<Term> grandParentTerms = generateTerms(2);
        addTermsAndSave(grandParentTerms, grandParent);
        final List<Term> allTerms = new ArrayList<>(directTerms);
        allTerms.addAll(parentTerms);
        allTerms.addAll(grandParentTerms);
        allTerms.sort(Comparator.comparing(Term::getLabel));

        final List<Term> result = sut
                .findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(allTerms, result);
    }

    @Test
    void findAllRootsIncludingImportsReturnsVocabularyRootTermsWhenVocabularyDoesNotImportAnyOther() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<Term> result = sut
                .findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
    }

    @Test
    void findAllIncludingImportsBySearchStringReturnsMatchingTermsFromVocabularyImportChain() {
        final Vocabulary parent = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Collections.singleton(parent.getUri()));
        final Vocabulary grandParent = Generator.generateVocabularyWithId();
        parent.setImportedVocabularies(Collections.singleton(grandParent.getUri()));
        transactional(() -> {
            em.merge(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parent, DescriptorFactory.vocabularyDescriptor(parent));
            em.persist(grandParent, DescriptorFactory.vocabularyDescriptor(grandParent));
        });
        final List<Term> directTerms = generateTerms(4);
        addTermsAndSave(directTerms, vocabulary);
        final List<Term> allTerms = new ArrayList<>(directTerms);
        final List<Term> parentTerms = generateTerms(3);
        addTermsAndSave(parentTerms, parent);
        allTerms.addAll(parentTerms);
        final List<Term> grandParentTerms = generateTerms(2);
        addTermsAndSave(grandParentTerms, grandParent);
        allTerms.addAll(grandParentTerms);
        // This would normally be inferred
        directTerms.forEach(dt -> dt.setVocabulary(vocabulary.getUri()));
        transactional(() -> {
            directTerms.get(0).setParentTerms(Collections.singleton(parentTerms.get(0)));
            parentTerms.get(0).setParentTerms(Collections.singleton(grandParentTerms.get(0)));
            directTerms.get(1).setParentTerms(Collections.singleton(parentTerms.get(1)));
            // Parents are in different contexts, so we have to deal with that
            em.merge(directTerms.get(0), DescriptorFactory.termDescriptor(vocabulary)
                                                          .addAttributeDescriptor(Term.getParentTermsField(),
                                                                  DescriptorFactory.vocabularyDescriptor(parent)));
            em.merge(directTerms.get(1), DescriptorFactory.termDescriptor(vocabulary)
                                                          .addAttributeDescriptor(Term.getParentTermsField(),
                                                                  DescriptorFactory.vocabularyDescriptor(parent)));
            em.merge(parentTerms.get(0), DescriptorFactory.termDescriptor(parent)
                                                          .addAttributeDescriptor(Term.getParentTermsField(),
                                                                  DescriptorFactory.vocabularyDescriptor(grandParent)));
            vocabulary.getGlossary().removeRootTerm(directTerms.get(0));
            vocabulary.getGlossary().removeRootTerm(directTerms.get(1));
            em.merge(vocabulary.getGlossary(), DescriptorFactory.glossaryDescriptor(vocabulary));
            parent.getGlossary().removeRootTerm(parentTerms.get(0));
            em.merge(parent.getGlossary(), DescriptorFactory.glossaryDescriptor(parent));
            insertNarrowerStatements(directTerms.get(0), directTerms.get(1), parentTerms.get(0));
        });

        final String searchString = directTerms.get(0).getLabel()
                                               .substring(0, directTerms.get(0).getLabel().length() - 2);
        final List<Term> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertFalse(result.isEmpty());
        assertThat(result.size(), lessThan(directTerms.size() + parentTerms.size() + grandParentTerms.size()));
        final List<Term> matching = allTerms.stream().filter(t -> t.getLabel().toLowerCase()
                                                                   .contains(searchString.toLowerCase())).collect(
                Collectors.toList());
        assertTrue(result.containsAll(matching));
    }

    @Test
    void persistSupportsReferencingParentTermInSameVocabulary() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            parent.setGlossary(vocabulary.getGlossary().getUri());
            vocabulary.getGlossary().addRootTerm(parent);
            em.persist(parent, DescriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), DescriptorFactory.glossaryDescriptor(vocabulary));
        });
        term.addParentTerm(parent);

        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getParentTerms());
    }

    @Test
    void persistSupportsReferencingParentTermInDifferentVocabulary() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Vocabulary parentVoc = Generator.generateVocabularyWithId();
        final Term parent = Generator.generateTermWithId(parentVoc.getUri());
        transactional(() -> {
            parentVoc.getGlossary().addRootTerm(parent);
            em.persist(parentVoc, DescriptorFactory.vocabularyDescriptor(parentVoc));
            parent.setGlossary(parentVoc.getGlossary().getUri());
            em.persist(parent, DescriptorFactory.termDescriptor(parentVoc));
        });
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);

        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getParentTerms());
        final TypedQuery<Boolean> query = em.createNativeQuery("ASK {GRAPH ?g {?t ?hasParent ?p .}}", Boolean.class)
                                            .setParameter("g", vocabulary.getUri()).setParameter("t", term.getUri())
                                            .setParameter("hasParent", URI.create(SKOS.BROADER))
                                            .setParameter("p", parent.getUri());
        assertTrue(query.getSingleResult());
    }

    @Test
    void updateSupportsReferencingParentTermInDifferentVocabulary() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Vocabulary parentVoc = Generator.generateVocabularyWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId(parentVoc.getUri());
        term.addParentTerm(parent);
        transactional(() -> {
            parentVoc.getGlossary().addRootTerm(parent);
            em.persist(parentVoc, DescriptorFactory.vocabularyDescriptor(parentVoc));
            parent.setGlossary(parentVoc.getGlossary().getUri());
            em.persist(parent, DescriptorFactory.termDescriptor(parentVoc));
            em.persist(term, DescriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
            addTermInVocabularyRelationship(parent, parentVoc.getUri());
        });

        final Term toUpdate = sut.find(term.getUri()).get();
        assertEquals(Collections.singleton(parent), toUpdate.getParentTerms());
        final String newDefinition = "Updated definition";
        toUpdate.setDefinition(newDefinition);
        transactional(() -> sut.update(toUpdate));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getParentTerms());
        assertEquals(newDefinition, result.getDefinition());
    }

    @Test
    void updateSupportsSettingNewParentFromAnotherDifferentVocabulary() {
        final Term term = Generator.generateTermWithId();
        final Term parentOne = Generator.generateTermWithId();
        final Vocabulary parentOneVoc = Generator.generateVocabularyWithId();
        final Vocabulary parentTwoVoc = Generator.generateVocabularyWithId();
        final Term parentTwo = Generator.generateTermWithId();
        term.addParentTerm(parentOne);
        transactional(() -> {
            parentOneVoc.getGlossary().addRootTerm(parentOne);
            em.persist(parentOneVoc, DescriptorFactory.vocabularyDescriptor(parentOneVoc));
            parentOne.setGlossary(parentOneVoc.getGlossary().getUri());
            em.persist(parentOne, DescriptorFactory.termDescriptor(parentOneVoc));
            em.persist(term, DescriptorFactory.termDescriptor(vocabulary));
            em.persist(parentTwoVoc, DescriptorFactory.vocabularyDescriptor(parentTwoVoc));
            parentTwo.setGlossary(parentTwoVoc.getGlossary().getUri());
            em.persist(parentTwo, DescriptorFactory.termDescriptor(parentTwoVoc));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
            addTermInVocabularyRelationship(parentOne, parentOneVoc.getUri());
            addTermInVocabularyRelationship(parentTwo, parentTwoVoc.getUri());
        });

        em.getEntityManagerFactory().getCache().evictAll();
        parentTwo.setVocabulary(parentTwoVoc.getUri());
        final Term toUpdate = sut.find(term.getUri()).get();
        assertEquals(Collections.singleton(parentOne), toUpdate.getParentTerms());
        toUpdate.setParentTerms(Collections.singleton(parentTwo));
        transactional(() -> sut.update(toUpdate));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parentTwo), result.getParentTerms());
    }

    @Test
    void findAllLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();

        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(2, result.size());
        final Optional<Term> parentResult = result.stream().filter(t -> t.equals(parent)).findFirst();
        assertTrue(parentResult.isPresent());
        assertEquals(parent.getSubTerms(), parentResult.get().getSubTerms());
    }

    private Term persistParentWithChild() {
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId();
        child.setGlossary(vocabulary.getGlossary().getUri());
        child.setParentTerms(Collections.singleton(parent));
        parent.setSubTerms(Collections.singleton(new TermInfo(child)));
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), DescriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(parent, DescriptorFactory.termDescriptor(vocabulary));
            em.persist(child, DescriptorFactory.termDescriptor(vocabulary));
            insertNarrowerStatements(child);
            addTermInVocabularyRelationship(parent, vocabulary.getUri());
            addTermInVocabularyRelationship(child, vocabulary.getUri());
        });
        return parent;
    }

    @Test
    void findAllRootsLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();
        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(parent, result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findAllRootsIncludingImportsLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();
        final List<Term> result = sut
                .findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(parent, result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findAllBySearchStringLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();
        final String searchString = parent.getLabel();
        final List<Term> result = sut.findAll(searchString, vocabulary);
        assertEquals(1, result.size());
        assertEquals(parent, result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findAllIncludingImportsBySearchStringLoadsSubTermsForResults() {
        final Term parent = persistParentWithChild();
        final String searchString = parent.getLabel();
        final List<Term> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertEquals(1, result.size());
        assertEquals(parent, result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findLoadsSubTermsForResult() {
        final Term parent = persistParentWithChild();
        final Optional<Term> result = sut.find(parent.getUri());
        assertTrue(result.isPresent());
        assertEquals(parent.getSubTerms(), result.get().getSubTerms());
    }

    @Test
    void termSupportsSimpleLiteralSources() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Set<String> sources = new HashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "mpp/navrh/c-3/h-0/p-36/o-2"));
        term.setSources(sources);
        transactional(() -> sut.persist(term, vocabulary));

        transactional(() -> verifyTermSourceStatements(term));

        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();
        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(sources, result.getSources());
    }

    private void verifyTermSourceStatements(Term term) {
        final Repository repository = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repository.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            final IRI subject = vf.createIRI(term.getUri().toString());
            final IRI hasSource = vf.createIRI(DC.Terms.SOURCE);
            final List<Statement> sourceStatements = Iterations.asList(conn.getStatements(subject, hasSource, null));
            assertEquals(term.getSources().size(), sourceStatements.size());
            sourceStatements.forEach(ss -> {
                assertTrue(term.getSources().contains(ss.getObject().stringValue()));
                if (ss.getObject() instanceof Literal) {
                    final Literal litSource = (Literal) ss.getObject();
                    assertFalse(litSource.getLanguage().isPresent());
                    assertEquals(XMLSchema.STRING, litSource.getDatatype());
                } else {
                    assertTrue(ss.getObject() instanceof IRI);
                }
            });
        }
    }

    @Test
    void updateAllowsSettingMultipleTermParentsFromMultipleVocabularies() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term parentOne = Generator.generateTermWithId(vocabulary.getUri());
        final Vocabulary vocabularyTwo = Generator.generateVocabularyWithId();
        final Term parentTwo = Generator.generateTermWithId(vocabularyTwo.getUri());
        transactional(() -> {
            em.persist(vocabularyTwo, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parentTwo, DescriptorFactory.termDescriptor(parentTwo));
            em.persist(parentOne, DescriptorFactory.termDescriptor(parentOne));
            em.persist(term, DescriptorFactory.termDescriptor(term));
        });

        term.addParentTerm(parentOne);
        term.addParentTerm(parentTwo);
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri());
        assertThat(result.getParentTerms(), hasItems(parentOne, parentTwo));
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                assertTrue(conn.hasStatement(vf.createIRI(term.getUri().toString()), vf.createIRI(SKOS.BROADER),
                        vf.createIRI(parentOne.getUri().toString()), false,
                        vf.createIRI(vocabulary.getUri().toString())));
                assertTrue(conn.hasStatement(vf.createIRI(term.getUri().toString()), vf.createIRI(SKOS.BROADER),
                        vf.createIRI(parentTwo.getUri().toString()), false,
                        vf.createIRI(vocabulary.getUri().toString())));
            }
        });
    }

    @Test
    void findAllRootsRetrievesRootTermsAndTermsSpecifiedByProvidedIdentifiers() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Term childToReturn = Generator.generateTermWithId(vocabulary.getUri());
        childToReturn.addParentTerm(terms.get(0));
        transactional(() -> em.persist(childToReturn, DescriptorFactory.termDescriptor(childToReturn)));

        final List<Term> results = sut
                .findAllRoots(vocabulary, PageRequest.of(0, 5), Collections.singleton(childToReturn.getUri()));
        assertFalse(results.isEmpty());
        assertThat(results, hasItem(childToReturn));
    }

    @Test
    void findAllRootsIncludingImportsRetrievesRootTermsAndTermsSpecifiedByProvidedIdentifiers() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Vocabulary parentVoc = Generator.generateVocabularyWithId();
        final Term parentTermInParentVoc = Generator.generateTermWithId(parentVoc.getUri());
        final Term childTermInParentVoc = Generator.generateTermWithId(parentVoc.getUri());
        childTermInParentVoc.addParentTerm(parentTermInParentVoc);
        vocabulary.setImportedVocabularies(Collections.singleton(parentVoc.getUri()));
        transactional(() -> {
            em.merge(vocabulary, DescriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parentVoc, DescriptorFactory.vocabularyDescriptor(parentVoc));
            em.persist(parentTermInParentVoc, DescriptorFactory.termDescriptor(parentTermInParentVoc));
            em.persist(childTermInParentVoc, DescriptorFactory.termDescriptor(childTermInParentVoc));
        });

        final List<Term> results = sut
                .findAllRootsIncludingImports(vocabulary, PageRequest.of(0, 5),
                        Collections.singleton(childTermInParentVoc.getUri()));
        assertFalse(results.isEmpty());
        assertThat(results, hasItem(childTermInParentVoc));
    }

    @Test
    void findLastEditedLoadsVocabularyForTerms() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final PersistChangeRecord persistRecord = Generator.generatePersistChange(term);
        persistRecord.setAuthor(Generator.generateUserWithId());
        transactional(() -> {
            em.persist(term, DescriptorFactory.termDescriptor(vocabulary));
            em.persist(persistRecord.getAuthor());
            em.persist(persistRecord);
        });

        final List<RecentlyModifiedAsset> result = sut.findLastEdited(1);
        assertFalse(result.isEmpty());
        assertEquals(term.getUri(), result.get(0).getUri());
        assertEquals(term.getVocabulary(), result.get(0).getVocabulary());
    }
}
