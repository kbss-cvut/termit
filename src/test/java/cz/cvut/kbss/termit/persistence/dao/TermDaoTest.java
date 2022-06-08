package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermDaoTest extends BaseTermDaoTestRunner {

    @Autowired
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void findAllRootsWithDefaultPageSpecReturnsAllTerms() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(terms), result);
    }

    private static List<TermDto> toDtos(List<Term> terms) {
        return Environment.termsToDtos(terms);
    }

    private void addTermsAndSave(Collection<Term> terms, Vocabulary vocabulary) {
        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            terms.forEach(t -> {
                t.setGlossary(vocabulary.getGlossary().getUri());
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                addTermInVocabularyRelationship(t, vocabulary.getUri());
            });
        });
    }

    private List<Term> generateTerms(int count) {
        return IntStream.range(0, count).mapToObj(i -> Generator.generateTermWithId())
                        .sorted(Comparator.comparing((Term t) -> t.getLabel().get(Environment.LANGUAGE)))
                        .collect(Collectors.toList());
    }

    private void addTermInVocabularyRelationship(Term term, URI vocabularyIri) {
        Generator.addTermInVocabularyRelationship(term, vocabularyIri, em);
    }

    @Test
    void findAllRootsReturnsMatchingPageWithTerms() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        // Paging starts at 0
        final List<TermDto> result = sut
                .findAllRoots(vocabulary, PageRequest.of(1, terms.size() / 2), Collections.emptyList());
        final List<Term> subList = terms.subList(terms.size() / 2, terms.size());
        assertEquals(toDtos(subList), result);
    }

    @Test
    void findAllRootsReturnsOnlyTermsInSpecifiedVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Vocabulary another = Generator.generateVocabulary();
        another.setUri(Generator.generateUri());
        another.getGlossary().setRootTerms(generateTerms(4).stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> em.persist(another));

        final List<TermDto> result = sut
                .findAllRoots(vocabulary, PageRequest.of(0, terms.size() / 2), Collections.emptyList());
        assertEquals(terms.size() / 2, result.size());
        assertTrue(toDtos(terms).containsAll(result));
    }

    @Test
    void findAllRootsWithoutVocabularyReturnsMatchingPageWithTerms() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        // Paging starts at 0
        final List<TermDto> result = sut
                .findAllRoots(PageRequest.of(1, terms.size() / 2), Collections.emptyList());
        final List<Term> subList = terms.subList(terms.size() / 2, terms.size());
        assertEquals(toDtos(subList), result);
    }

    @Test
    void findAllRootsWithoutVocabularyReturnsOnlyRootTerms() {
        final List<Term> rootTerms = generateTerms(10);
        addTermsAndSave(new HashSet<>(rootTerms), vocabulary);
        transactional(() -> rootTerms.forEach(t -> {
            final Term child = Generator.generateTermWithId(vocabulary.getUri());
            child.setParentTerms(Collections.singleton(t));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
        }));

        final Vocabulary vocabulary2 = Generator.generateVocabulary();
        vocabulary2.setUri(Generator.generateUri());
        transactional(() -> em.persist(vocabulary2, descriptorFactory.vocabularyDescriptor(vocabulary2)));
        final List<Term> rootTerms2 = generateTerms(10);
        addTermsAndSave(new HashSet<>(rootTerms2), vocabulary2);
        transactional(() -> rootTerms.forEach(t -> {
            final Term child = Generator.generateTermWithId(vocabulary2.getUri());
            child.setExternalParentTerms(Collections.singleton(t));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary2));
        }));

        final List<TermDto> result = sut.findAllRoots(Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        final List<TermDto> set = new ArrayList<>();
        set.addAll(toDtos(rootTerms));
        set.addAll(toDtos(rootTerms2));
        set.sort(Comparator.comparing(o -> o.getLabel().get()));
        assertEquals(set, result);
    }

    @Test
    void findAllRootsReturnsOnlyRootTerms() {
        final List<Term> rootTerms = generateTerms(10);
        addTermsAndSave(new HashSet<>(rootTerms), vocabulary);
        transactional(() -> rootTerms.forEach(t -> {
            final Term child = Generator.generateTermWithId(vocabulary.getUri());
            child.setParentTerms(Collections.singleton(t));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
        }));


        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(rootTerms), result);
    }

    @Test
    void findAllBySearchStringReturnsTermsWithMatchingLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<TermDto> result = sut.findAll(terms.get(0).getLabel().get(Environment.LANGUAGE), vocabulary);
        assertEquals(1, result.size());
        assertTrue(toDtos(terms).contains(result.get(0)));
    }

    @Test
    void findAllBySearchStringWithoutVocabularyReturnsTermsWithMatchingLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<TermDto> result = sut.findAll(terms.get(0).getLabel().get(Environment.LANGUAGE));
        assertEquals(1, result.size());
        assertTrue(toDtos(terms).contains(result.get(0)));
    }

    @Test
    void findAllBySearchStringReturnsTermsWithMatchingLabelWhichAreNotRoots() {
        enableRdfsInference(em);
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);
        final Term root = terms.get(Generator.randomIndex(terms));
        final Term child = Generator.generateTermWithId(vocabulary.getUri());
        child.setPrimaryLabel("test");
        child.setParentTerms(Collections.singleton(root));
        child.setGlossary(vocabulary.getGlossary().getUri());
        final Term matchingDesc = Generator.generateTermWithId();
        matchingDesc.setPrimaryLabel("Metropolitan plan");
        matchingDesc.setParentTerms(Collections.singleton(child));
        matchingDesc.setGlossary(vocabulary.getGlossary().getUri());
        transactional(() -> {
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
            em.persist(matchingDesc, descriptorFactory.termDescriptor(vocabulary));
        });

        final List<TermDto> result = sut.findAll("plan", vocabulary);
        assertEquals(1, result.size());
        assertEquals(new TermDto(matchingDesc), result.get(0));
    }

    @Test
    void existsInVocabularyReturnsTrueForLabelExistingInVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final String label = terms.get(0).getLabel().get(Environment.LANGUAGE);
        assertTrue(sut.existsInVocabulary(label, vocabulary, Environment.LANGUAGE));
    }

    @Test
    void existsInVocabularyReturnsFalseForUnknownLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        assertFalse(sut.existsInVocabulary("unknown label", vocabulary, Environment.LANGUAGE));
    }

    @Test
    void existsInVocabularyReturnsTrueWhenLabelDiffersOnlyInCase() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final String label = terms.get(0).getLabel().get(Environment.LANGUAGE).toLowerCase();
        assertTrue(sut.existsInVocabulary(label, vocabulary, Environment.LANGUAGE));
    }

    @Test
    void existsInVocabularyReturnsFalseForLabelExistingInAnotherLanguageInTheVocabulary() {
        final Term term = Generator.generateMultiLingualTerm("en", "cs");
        final List<Term> terms = Collections.singletonList(term);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final String label = terms.get(0).getLabel().get("en");
        assertTrue(sut.existsInVocabulary(label, vocabulary, "en"));
        assertFalse(sut.existsInVocabulary(label, vocabulary, "cs"));
    }


    @Test
    void findAllFullGetsAllTermsInVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final List<Term> result = sut.findAllFull(vocabulary);
        assertEquals(terms.size(), result.size());
        assertThat(result, hasItems(terms.toArray(new Term[]{})));
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
    void findAllFullReturnsAllTermsFromVocabularyOrderedByLabel() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final List<Term> result = sut.findAllFull(vocabulary);
        terms.sort(Comparator.comparing(Term::getPrimaryLabel));
        assertEquals(terms, result);
    }

    @Test
    void findAllIncludingImportedReturnsTermsInVocabularyAndImportedVocabularies() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);
        final Vocabulary parent = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Collections.singleton(parent.getUri()));
        transactional(() -> {
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.vocabularyDescriptor(parent));
        });
        final List<Term> parentTerms = generateTerms(3);
        addTermsAndSave(parentTerms, parent);

        final List<TermDto> result = sut.findAllIncludingImported(vocabulary);
        final List<Term> allExpected = new ArrayList<>(terms);
        allExpected.addAll(parentTerms);
        allExpected.sort(Comparator.comparing(Term::getPrimaryLabel));
        assertEquals(toDtos(allExpected), result);
    }

    @Test
    void persistSavesTermIntoVocabularyContext() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertNotNull(result);
        assertEquals(term, result);
    }

    @Test
    void persistEnsuresVocabularyAttributeIsEmptySoThatItCanBeInferred() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> sut.persist(term, vocabulary));
        assertNull(term.getVocabulary());

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertNotNull(result);
        // Term vocabulary should be null here, as we have inference disabled
        assertNull(result.getVocabulary());
    }

    @Test
    void updateUpdatesTermInVocabularyContext() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(term);
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
        });

        final String updatedLabel = "Updated label";
        final String oldLabel = term.getLabel().get(Environment.LANGUAGE);
        term.setPrimaryLabel(updatedLabel);
        em.getEntityManagerFactory().getCache().evictAll();
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertEquals(updatedLabel, result.getLabel().get(Environment.LANGUAGE));
        assertFalse(em.createNativeQuery("ASK WHERE { ?x ?hasLabel ?label }", Boolean.class)
                      .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))
                      .setParameter("label", oldLabel, Environment.LANGUAGE).getSingleResult());
    }

    @Test
    void findAllRootsReturnsOnlyTermsWithMatchingLabelLanguage() {
        final List<Term> terms = generateTerms(5);
        final Term foreignLabelTerm = Generator.generateTermWithId();
        final List<Term> allTerms = new ArrayList<>(terms);
        allTerms.add(foreignLabelTerm);
        addTermsAndSave(allTerms, vocabulary);
        transactional(() -> insertForeignLabel(foreignLabelTerm));

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(terms), result);
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

        final List<Term> result = sut.findAllFull(vocabulary);
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
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.vocabularyDescriptor(parent));
            em.persist(grandParent, descriptorFactory.vocabularyDescriptor(grandParent));
        });
        final List<Term> parentTerms = generateTerms(3);
        addTermsAndSave(parentTerms, parent);
        final List<Term> grandParentTerms = generateTerms(2);
        addTermsAndSave(grandParentTerms, grandParent);
        final List<Term> allTerms = new ArrayList<>(directTerms);
        allTerms.addAll(parentTerms);
        allTerms.addAll(grandParentTerms);
        allTerms.sort(Comparator.comparing(Term::getPrimaryLabel));

        final List<TermDto> result = sut
                .findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(allTerms), result);
    }

    @Test
    void findAllRootsIncludingImportsReturnsVocabularyRootTermsWhenVocabularyDoesNotImportAnyOther() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(new HashSet<>(terms), vocabulary);

        final List<TermDto> result = sut
                .findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(toDtos(terms), result);
    }

    @Test
    void findAllIncludingImportsBySearchStringReturnsMatchingTermsFromVocabularyImportChain() {
        enableRdfsInference(em);
        final Vocabulary parent = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Collections.singleton(parent.getUri()));
        final Vocabulary grandParent = Generator.generateVocabularyWithId();
        parent.setImportedVocabularies(Collections.singleton(grandParent.getUri()));
        transactional(() -> {
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.vocabularyDescriptor(parent));
            em.persist(grandParent, descriptorFactory.vocabularyDescriptor(grandParent));
        });
        final List<Term> directTerms = generateTerms(4);
        addTermsAndSave(directTerms, vocabulary);
        final List<Term> allTerms = new ArrayList<>(directTerms);
        final List<Term> parentTerms = generateTerms(3);
        addTermsAndSave(parentTerms, parent);
        // This is normally inferred
        parentTerms.forEach(pt -> pt.setVocabulary(parent.getUri()));
        allTerms.addAll(parentTerms);
        final List<Term> grandParentTerms = generateTerms(2);
        addTermsAndSave(grandParentTerms, grandParent);
        allTerms.addAll(grandParentTerms);
        // This is normally inferred
        directTerms.forEach(dt -> dt.setVocabulary(vocabulary.getUri()));
        transactional(() -> {
            directTerms.get(0).setExternalParentTerms(Collections.singleton(parentTerms.get(0)));
            parentTerms.get(0).setExternalParentTerms(Collections.singleton(grandParentTerms.get(0)));
            directTerms.get(1).setExternalParentTerms(Collections.singleton(parentTerms.get(1)));
            // Parents are in different contexts, so we have to deal with that
            em.merge(directTerms.get(0), descriptorFactory.termDescriptor(vocabulary)
                                                          .addAttributeDescriptor(descriptorFactory
                                                                                          .fieldSpec(Term.class,
                                                                                                     "externalParentTerms"),
                                                                                  descriptorFactory.vocabularyDescriptor(
                                                                                          parent)));
            em.merge(directTerms.get(1), descriptorFactory.termDescriptor(vocabulary)
                                                          .addAttributeDescriptor(descriptorFactory
                                                                                          .fieldSpec(Term.class,
                                                                                                     "externalParentTerms"),
                                                                                  descriptorFactory.vocabularyDescriptor(
                                                                                          parent)));
            em.merge(parentTerms.get(0), descriptorFactory.termDescriptor(parent)
                                                          .addAttributeDescriptor(descriptorFactory
                                                                                          .fieldSpec(Term.class,
                                                                                                     "externalParentTerms"),
                                                                                  descriptorFactory.vocabularyDescriptor(
                                                                                          grandParent)));
            vocabulary.getGlossary().removeRootTerm(directTerms.get(0));
            vocabulary.getGlossary().removeRootTerm(directTerms.get(1));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            parent.getGlossary().removeRootTerm(parentTerms.get(0));
            em.merge(parent.getGlossary(), descriptorFactory.glossaryDescriptor(parent));
        });

        final String searchString = directTerms.get(0).getPrimaryLabel()
                                               .substring(0, directTerms.get(0).getPrimaryLabel().length() - 2);
        final List<TermDto> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertFalse(result.isEmpty());
        assertThat(result.size(), lessThan(directTerms.size() + parentTerms.size() + grandParentTerms.size()));
        final List<Term> matching = allTerms.stream().filter(t -> t.getPrimaryLabel().toLowerCase()
                                                                   .contains(searchString.toLowerCase())).collect(
                Collectors.toList());
        assertTrue(result.containsAll(toDtos(matching)));
    }

    @Test
    void persistSupportsReferencingParentTermInSameVocabulary() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            parent.setGlossary(vocabulary.getGlossary().getUri());
            vocabulary.getGlossary().addRootTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });
        term.setParentTerms(Collections.singleton(parent));

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
        parent.setGlossary(parentVoc.getGlossary().getUri());
        transactional(() -> {
            parentVoc.getGlossary().addRootTerm(parent);
            em.persist(parentVoc, descriptorFactory.vocabularyDescriptor(parentVoc));
            parent.setGlossary(parentVoc.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(parentVoc));
        });
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.addParentTerm(parent);

        transactional(() -> sut.persist(term, vocabulary));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getExternalParentTerms());
        final TypedQuery<Boolean> query = em.createNativeQuery("ASK {GRAPH ?g {?t ?hasParent ?p .}}", Boolean.class)
                                            .setParameter("g", vocabulary.getUri()).setParameter("t", term.getUri())
                                            .setParameter("hasParent", URI.create(SKOS.BROAD_MATCH))
                                            .setParameter("p", parent.getUri());
        assertTrue(query.getSingleResult());
    }

    @Test
    void updateSupportsReferencingParentTermInDifferentVocabulary() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Vocabulary parentVoc = Generator.generateVocabularyWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId(parentVoc.getUri());
        transactional(() -> {
            parentVoc.getGlossary().addRootTerm(parent);
            em.persist(parentVoc, descriptorFactory.vocabularyDescriptor(parentVoc));
            parent.setGlossary(parentVoc.getGlossary().getUri());
            term.addParentTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(parentVoc));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
            addTermInVocabularyRelationship(parent, parentVoc.getUri());
        });

        final Term toUpdate = sut.find(term.getUri()).get();
        assertEquals(Collections.singleton(parent), toUpdate.getExternalParentTerms());
        final MultilingualString newDefinition = MultilingualString
                .create("Updated definition", Environment.LANGUAGE);
        toUpdate.setDefinition(newDefinition);
        transactional(() -> sut.update(toUpdate));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parent), result.getExternalParentTerms());
        assertEquals(newDefinition, result.getDefinition());
    }

    @Test
    void updateSupportsSettingNewParentFromAnotherDifferentVocabulary() {
        final Term term = Generator.generateTermWithId();
        final Term parentOne = Generator.generateTermWithId();
        final Vocabulary parentOneVoc = Generator.generateVocabularyWithId();
        final Vocabulary parentTwoVoc = Generator.generateVocabularyWithId();
        final Term parentTwo = Generator.generateTermWithId();
        transactional(() -> {
            parentOneVoc.getGlossary().addRootTerm(parentOne);
            em.persist(parentOneVoc, descriptorFactory.vocabularyDescriptor(parentOneVoc));
            em.persist(parentTwoVoc, descriptorFactory.vocabularyDescriptor(parentTwoVoc));
            parentOne.setGlossary(parentOneVoc.getGlossary().getUri());
            term.addParentTerm(parentOne);
            em.persist(parentOne, descriptorFactory.termDescriptor(parentOneVoc));
            em.persist(parentTwo, descriptorFactory.termDescriptor(parentTwoVoc));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            parentTwo.setGlossary(parentTwoVoc.getGlossary().getUri());
            addTermInVocabularyRelationship(term, vocabulary.getUri());
            addTermInVocabularyRelationship(parentOne, parentOneVoc.getUri());
            addTermInVocabularyRelationship(parentTwo, parentTwoVoc.getUri());
        });

        em.getEntityManagerFactory().getCache().evictAll();
        parentTwo.setVocabulary(parentTwoVoc.getUri());
        final Term toUpdate = sut.find(term.getUri()).get();
        assertEquals(Collections.singleton(parentOne), toUpdate.getExternalParentTerms());
        toUpdate.setExternalParentTerms(Collections.singleton(parentTwo));
        transactional(() -> sut.update(toUpdate));

        final Term result = em.find(Term.class, term.getUri());
        assertNotNull(result);
        assertEquals(Collections.singleton(parentTwo), result.getExternalParentTerms());
    }

    @Test
    void findAllLoadsSubTermsForResults() {
        enableRdfsInference(em);
        final Term parent = persistParentWithChild();

        final List<Term> result = sut.findAllFull(vocabulary);
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
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(child, descriptorFactory.termDescriptor(vocabulary));
        });
        return parent;
    }

    @Test
    void findAllRootsOrdersResultsInLexicographicOrderForCzech() {
        configuration.getPersistence().setLanguage("cs");
        persistTerms("cs", "Německo", "Čína", "Španělsko", "Sýrie");
        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(4, result.size());
        assertEquals(Arrays
                             .asList("Čína", "Německo", "Sýrie", "Španělsko"),
                     result.stream().map(r -> r.getLabel().get("cs"))
                           .collect(Collectors.toList()));
    }

    private void persistTerms(String lang, String... labels) {
        transactional(() -> Arrays.stream(labels).forEach(label -> {
            final Term parent = Generator.generateTermWithId();
            parent.getLabel().set(lang, label);
            parent.setGlossary(vocabulary.getGlossary().getUri());
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(parent, vocabulary.getUri());
        }));
    }

    @Test
    void findAllRootsLoadsSubTermsForResults() {
        enableRdfsInference(em);
        final Term parent = persistParentWithChild();
        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(new TermDto(parent), result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findAllRootsIncludingImportsLoadsSubTermsForResults() {
        enableRdfsInference(em);
        final Term parent = persistParentWithChild();
        final List<TermDto> result = sut
                .findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(new TermDto(parent), result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findAllBySearchStringLoadsSubTermsForResults() {
        enableRdfsInference(em);
        final Term parent = persistParentWithChild();
        final String searchString = parent.getPrimaryLabel();
        final List<TermDto> result = sut.findAll(searchString, vocabulary);
        assertEquals(1, result.size());
        assertEquals(new TermDto(parent), result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findAllIncludingImportsBySearchStringLoadsSubTermsForResults() {
        enableRdfsInference(em);
        final Term parent = persistParentWithChild();
        final String searchString = parent.getPrimaryLabel();
        final List<TermDto> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertEquals(1, result.size());
        assertEquals(new TermDto(parent), result.get(0));
        assertEquals(parent.getSubTerms(), result.get(0).getSubTerms());
    }

    @Test
    void findLoadsSubTermsForResult() {
        enableRdfsInference(em);
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
                    assertEquals(XSD.STRING, litSource.getDatatype());
                } else {
                    assertTrue(ss.getObject() instanceof IRI);
                }
            });
        }
    }

    @Test
    void updateAllowsSettingMultipleTermParentsFromMultipleVocabularies() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parentOne = Generator.generateTermWithId(vocabulary.getUri());
        parentOne.setGlossary(vocabulary.getGlossary().getUri());
        final Vocabulary vocabularyTwo = Generator.generateVocabularyWithId();
        final Term parentTwo = Generator.generateTermWithId(vocabularyTwo.getUri());
        transactional(() -> {
            em.persist(vocabularyTwo, descriptorFactory.vocabularyDescriptor(vocabulary));
            parentTwo.setGlossary(vocabularyTwo.getGlossary().getUri());
            em.persist(parentOne, descriptorFactory.termDescriptor(parentOne));
            em.persist(parentTwo, descriptorFactory.termDescriptor(parentTwo));
            em.persist(term, descriptorFactory.termDescriptor(term));
        });

        term.addParentTerm(parentOne);
        term.addParentTerm(parentTwo);
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri());
        assertThat(result.getParentTerms(), hasItem(parentOne));
        assertThat(result.getExternalParentTerms(), hasItem(parentTwo));
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                assertTrue(conn.hasStatement(vf.createIRI(term.getUri().toString()), vf.createIRI(SKOS.BROADER),
                                             vf.createIRI(parentOne.getUri().toString()), false,
                                             vf.createIRI(vocabulary.getUri().toString())));
                assertTrue(conn.hasStatement(vf.createIRI(term.getUri().toString()), vf.createIRI(SKOS.BROAD_MATCH),
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
        transactional(() -> em.persist(childToReturn, descriptorFactory.termDescriptor(childToReturn)));

        final List<TermDto> results = sut
                .findAllRoots(vocabulary, PageRequest.of(0, 5), Collections.singleton(childToReturn.getUri()));
        assertFalse(results.isEmpty());
        assertThat(results, hasItem(new TermDto(childToReturn)));
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
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(parentVoc, descriptorFactory.vocabularyDescriptor(parentVoc));
            em.persist(parentTermInParentVoc, descriptorFactory.termDescriptor(parentTermInParentVoc));
            em.persist(childTermInParentVoc, descriptorFactory.termDescriptor(childTermInParentVoc));
        });

        final List<TermDto> results = sut
                .findAllRootsIncludingImports(vocabulary, PageRequest.of(0, 5),
                                              Collections.singleton(childTermInParentVoc.getUri()));
        assertFalse(results.isEmpty());
        assertThat(results, hasItem(new TermDto(childTermInParentVoc)));
    }

    @Test
    void findLastEditedLoadsVocabularyForTerms() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final PersistChangeRecord persistRecord = Generator.generatePersistChange(term);
        persistRecord.setAuthor(Generator.generateUserWithId());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(persistRecord.getAuthor());
            em.persist(persistRecord);
        });

        final List<RecentlyModifiedAsset> result = sut.findLastEdited(1);
        assertFalse(result.isEmpty());
        assertEquals(term.getUri(), result.get(0).getUri());
        assertEquals(vocabulary.getUri(), result.get(0).getVocabulary());
    }

    @Test
    void updateSupportsUpdatingPluralMultilingualAltLabels() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final MultilingualString altOne = MultilingualString.create("Budova", "cs");
        term.setAltLabels(new HashSet<>(Collections.singleton(altOne)));
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(vocabulary)));

        altOne.set("en", "Building");
        final MultilingualString altTwo = MultilingualString.create("Construction", "en");
        altTwo.set("cs", "Stavba");
        term.getAltLabels().add(altTwo);
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri(), descriptorFactory.termDescriptor(vocabulary));
        assertNotNull(result);
        // We have to check it this way because the loaded multilingual labels might be a different combination of the
        // translations
        final Map<String, Set<String>> allLabels = new HashMap<>();
        result.getAltLabels().forEach(alt -> alt.getValue().forEach((lang, val) -> {
            allLabels.putIfAbsent(lang, new HashSet<>());
            allLabels.get(lang).add(val);
        }));
        term.getAltLabels().forEach(alt -> alt.getValue().forEach((lang, val) -> {
            assertThat(allLabels, hasKey(lang));
            assertThat(allLabels.get(lang), hasItem(val));
        }));
    }

    // Bug #1459
    @Test
    void updateHandlesChangesToTermsWithInferredDefinitionSource() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final File file = Generator.generateFileWithId("test.html");
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> {
            em.persist(file);
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
        });
        transactional(() -> {
            final TermDefinitionSource source = saveDefinitionSource(term, file);
            // This is normally inferred
            term.setDefinitionSource(source);
        });
        final String newDefinition = "new definition";
        term.getDefinition().set(Environment.LANGUAGE, newDefinition);
        transactional(() -> sut.update(term));

        final Term result = em.find(Term.class, term.getUri());
        assertEquals(newDefinition, result.getDefinition().get(Environment.LANGUAGE));
        assertNotNull(result.getDefinitionSource());
    }

    private TermDefinitionSource saveDefinitionSource(Term term, File file) {
        final TermDefinitionSource source = new TermDefinitionSource(term.getUri(), new FileOccurrenceTarget(file));
        source.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test")));
        final Repository repo = em.unwrap(Repository.class);
        em.persist(source);
        em.persist(source.getTarget());
        try (final RepositoryConnection connection = repo.getConnection()) {
            // Simulates inference
            final ValueFactory vf = connection.getValueFactory();
            connection.add(vf.createIRI(term.getUri().toString()),
                           vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zdroj_definice_termu),
                           vf.createIRI(source.getUri().toString()));
        }
        return source;
    }

    @Test
    void subTermLoadingSortsThemByLabel() {
        enableRdfsInference(em);
        final Term parent = Generator.generateTermWithId();
        final List<Term> children = IntStream.range(0, 5).mapToObj(i -> {
            final Term child = Generator.generateTermWithId();
            child.setParentTerms(Collections.singleton(parent));
            child.setGlossary(vocabulary.getGlossary().getUri());
            return child;
        }).collect(Collectors.toList());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            children.forEach(child -> em.persist(child, descriptorFactory.termDescriptor(vocabulary)));
        });
        children.sort(Comparator.comparing(child -> child.getLabel().get(Environment.LANGUAGE)));

        final Optional<Term> result = sut.find(parent.getUri());
        assertTrue(result.isPresent());
        assertEquals(children.size(), result.get().getSubTerms().size());
        final Iterator<TermInfo> it = result.get().getSubTerms().iterator();
        for (Term child : children) {
            assertTrue(it.hasNext());
            final TermInfo next = it.next();
            assertEquals(child.getUri(), next.getUri());
        }
    }

    /**
     * Bug #1576
     */
    @Test
    void updateClearsPossiblyStaleTermDtoFromCache() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final String originalLabel = "Uppercase Test";
        term.getLabel().set(Environment.LANGUAGE, originalLabel);
        term.setGlossary(vocabulary.getGlossary().getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            addTermInVocabularyRelationship(term, vocabulary.getUri());
        });
        final List<TermDto> dto = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, dto.size());
        assertEquals(originalLabel, dto.get(0).getLabel().get(Environment.LANGUAGE));
        final String newLabel = originalLabel.toLowerCase();
        term.setLabel(MultilingualString.create(newLabel, Environment.LANGUAGE));
        transactional(() -> sut.update(term));
        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, result.size());
        assertEquals(newLabel, result.get(0).getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void loadsSubTermsForIncludedTermsLoadedWhenFetchingRoots() {
        enableRdfsInference(em);
        final List<Term> rootTerms = generateTerms(10);
        addTermsAndSave(rootTerms, vocabulary);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        // Make it last
        term.getLabel().set(Environment.LANGUAGE, "zzzzzz");
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term child = Generator.generateTermWithId(vocabulary.getUri());
        child.setGlossary(vocabulary.getGlossary().getUri());
        child.addParentTerm(term);
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(term));
            em.persist(child, descriptorFactory.termDescriptor(child));
        });

        final List<TermDto> result = sut.findAllRoots(vocabulary, PageRequest.of(0, rootTerms.size() / 2),
                                                      Collections.singleton(term.getUri()));
        final Optional<TermDto> toFind = result.stream().filter(dto -> term.getUri().equals(dto.getUri())).findFirst();
        assertTrue(toFind.isPresent());
        assertFalse(toFind.get().getSubTerms().isEmpty());
    }

    /**
     * Bug #1634
     */
    @Test
    void findAllRootsLoadsSubTermsForAncestorsOfIncludedTerms() {
        enableRdfsInference(em);
        final List<Term> rootTerms = generateTerms(10);
        addTermsAndSave(rootTerms, vocabulary);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        parent.setGlossary(vocabulary.getGlossary().getUri());
        // Ensure it is not loaded among roots on the first page
        term.getLabel().set(Environment.LANGUAGE, "zzzzzzzzz");
        term.addParentTerm(parent);
        transactional(() -> {
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(term));
            em.persist(parent, descriptorFactory.termDescriptor(parent));
        });

        final List<TermDto> result = sut.findAllRoots(vocabulary, PageRequest.of(0, rootTerms.size() / 2),
                                                      Collections.singleton(term.getUri()));
        final Optional<TermDto> toFind = result.stream().filter(dto -> term.getUri().equals(dto.getUri())).findFirst();
        assertTrue(toFind.isPresent());
        assertTrue(toFind.get().hasParentTerms());
        toFind.get().getParentTerms().forEach(pt -> {
            assertNotNull(pt.getSubTerms());
            assertTrue(pt.getSubTerms().stream().anyMatch(ti -> ti.getUri().equals(term.getUri())));
        });
    }

    /**
     * Bug #1634
     */
    @Test
    void findAllRootsEnsuresIncludedTermsAreNotDuplicatedInResult() {
        final List<Term> rootTerms = generateTerms(10);
        addTermsAndSave(rootTerms, vocabulary);
        rootTerms.sort(Comparator.comparing(Term::getPrimaryLabel));
        final Term toInclude = rootTerms.get(0);

        final List<TermDto> result = sut.findAllRoots(vocabulary, PageRequest.of(0, rootTerms.size() / 2),
                                                      Collections.singleton(toInclude.getUri()));
        assertEquals(1, (int) result.stream().filter(t -> t.getUri().equals(toInclude.getUri())).count());
    }

    @Test
    void findAllUnusedReturnsUrisOfTermsWithoutOccurrences() {
        cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term1 = Generator.generateTermWithId();
        term1.setVocabulary(vocabulary.getUri());
        final Term term2 = Generator.generateTermWithId();
        term2.setVocabulary(term1.getVocabulary());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument(file);
        final TermOccurrence to = Generator.generateTermOccurrence(term2, file, false);

        transactional(() -> {
            enableRdfsInference(em);
            em.persist(vocabulary);
            em.persist(file);
            em.persist(document);
            em.persist(to);
            em.persist(to.getTarget());
            em.persist(term1);
            em.persist(term2);
        });

        final List<URI> result = sut.findAllUnused(vocabulary);
        assertEquals(1, result.size());
        assertEquals(term1.getUri(), result.get(0));
    }

    private Document getDocument(final File... files) {
        final Document document = Generator.generateDocumentWithId();
        document.setLabel("Doc");
        Arrays.stream(files).forEach(document::addFile);
        return document;
    }

    @Test
    void findAllGetsAllTermsInVocabulary() {
        final List<Term> terms = generateTerms(10);
        addTermsAndSave(terms, vocabulary);

        final List<TermDto> result = sut.findAll(vocabulary);
        assertEquals(terms.size(), result.size());
        assertThat(result, hasItems(toDtos(terms).toArray(new TermDto[]{})));
    }

    @Test
    void updateWithChangeInParentsEvictsChangedParentsSubTermsCache() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            parent.setGlossary(vocabulary.getGlossary().getUri());
            term.addParentTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });
        final List<TermDto> rootsBefore = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC,
                                                           Collections.emptyList());
        assertEquals(1, rootsBefore.size());
        assertTrue(rootsBefore.get(0).getSubTerms().stream().anyMatch(ti -> ti.getUri().equals(term.getUri())));

        final Term toUpdate = sut.find(term.getUri()).get();
        assertEquals(Collections.singleton(parent), toUpdate.getParentTerms());
        toUpdate.getParentTerms().remove(parent);
        transactional(() -> {
            sut.update(toUpdate);
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        final List<TermDto> roots = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(2, roots.size());
        roots.forEach(r -> assertThat(r.getSubTerms(), anyOf(nullValue(), emptyCollectionOf(TermInfo.class))));
    }

    @Test
    void persistWithParentEvictsParentsSubTermsCache() {
        enableRdfsInference(em);
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            parent.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            addTermInVocabularyRelationship(parent, vocabulary.getUri());
        });
        final List<TermDto> rootsBefore = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC,
                                                           Collections.emptyList());
        assertEquals(1, rootsBefore.size());
        assertThat(rootsBefore.get(0).getSubTerms(), anyOf(nullValue(), emptyCollectionOf(TermInfo.class)));

        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        term.setParentTerms(Collections.singleton(parent));
        transactional(() -> sut.persist(term, vocabulary));

        final List<TermDto> roots = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, roots.size());
        assertTrue(roots.get(0).getSubTerms().stream().anyMatch(ti -> ti.getUri().equals(term.getUri())));
    }

    @Test
    void removeEvictsParentsSubTermsCache() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setGlossary(vocabulary.getGlossary().getUri());
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(parent);
            parent.setGlossary(vocabulary.getGlossary().getUri());
            term.addParentTerm(parent);
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });
        final List<TermDto> rootsBefore = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC,
                                                           Collections.emptyList());
        assertEquals(1, rootsBefore.size());
        assertTrue(rootsBefore.get(0).getSubTerms().stream().anyMatch(ti -> ti.getUri().equals(term.getUri())));

        transactional(() -> sut.remove(term));

        final List<TermDto> roots = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(1, roots.size());
        assertThat(roots.get(0).getSubTerms(), anyOf(nullValue(), emptyCollectionOf(TermInfo.class)));
    }
}
