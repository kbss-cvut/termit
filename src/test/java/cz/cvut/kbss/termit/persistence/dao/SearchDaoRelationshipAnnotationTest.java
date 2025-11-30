package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static cz.cvut.kbss.termit.environment.Environment.setPrimaryLabel;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchDaoRelationshipAnnotationTest extends BaseDaoTestRunner {
    private static boolean initialized = false;
    private static User user;
    private static Vocabulary vocabulary;
    private static Term termA;
    private static Term termB;
    private static Term termC;
    private static Term annotatingTerm;
    private static CustomAttribute customAttribute;

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private DataDao dataDao;

    private SearchDao sut;

    @BeforeEach
    void setUp() {
        sut = new SearchDao(em, dataDao);

        if (!initialized) {
            user = Generator.generateUserWithId();
            vocabulary = Generator.generateVocabularyWithId();
            termA = Generator.generateTermWithId();
            termB = Generator.generateTermWithId();
            termC = Generator.generateTermWithId();
            annotatingTerm = Generator.generateTermWithId();

            setPrimaryLabel(termA, "Term A");
            setPrimaryLabel(termB, "Term B");
            setPrimaryLabel(termC, "Term C");
            setPrimaryLabel(annotatingTerm, "Annotating Term");

            termA.setVocabulary(vocabulary.getUri());
            termB.setVocabulary(vocabulary.getUri());
            termC.setVocabulary(vocabulary.getUri());
            annotatingTerm.setVocabulary(vocabulary.getUri());

            vocabulary.getGlossary().addRootTerm(termA);
            vocabulary.getGlossary().addRootTerm(termB);
            vocabulary.getGlossary().addRootTerm(termC);
            vocabulary.getGlossary().addRootTerm(annotatingTerm);

            customAttribute = new CustomAttribute();
            customAttribute.setUri(URI.create(cz.cvut.kbss.termit.util.Vocabulary.ONTOLOGY_IRI_TERMIT + "/custom-attribute/annotatedBy"));
            customAttribute.setLabel(MultilingualString.create("Annotated By", Environment.LANGUAGE));
            customAttribute.setDomain(URI.create(RDF.STATEMENT));
            customAttribute.setRange(URI.create(SKOS.CONCEPT));

            transactional(() -> {
                em.persist(user);
                em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
                em.persist(termA, descriptorFactory.termDescriptor(termA));
                em.persist(termB, descriptorFactory.termDescriptor(termB));
                em.persist(termC, descriptorFactory.termDescriptor(termC));
                em.persist(annotatingTerm, descriptorFactory.termDescriptor(annotatingTerm));
                em.persist(customAttribute);

                termA.addRelatedTerm(new TermInfo(termB));
                em.merge(termA, descriptorFactory.termDescriptor(termA));

                addRelationshipAnnotation(termA.getUri(), URI.create(SKOS.RELATED), termB.getUri(),
                                         customAttribute.getUri(), annotatingTerm.getUri(),
                                         vocabulary.getUri());
            });
            initialized = true;
        }
        Environment.setCurrentUser(user);
    }

    private void addRelationshipAnnotation(URI subject, URI predicate, URI object,
                                           URI annotationProperty, URI annotationValue,
                                           URI context) {
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();

        try (RepositoryConnection conn = repo.getConnection()) {
            conn.begin();

            Statement stmt = vf.createStatement(
                vf.createTriple(
                    vf.createIRI(subject.toString()),
                    vf.createIRI(predicate.toString()),
                    vf.createIRI(object.toString())
                ),
                vf.createIRI(annotationProperty.toString()),
                vf.createIRI(annotationValue.toString())
            );

            conn.add(stmt, vf.createIRI(context.toString()));
            conn.commit();
        }
    }

    @Test
    void facetedTermSearchFindsTermsWhoseRelationshipsAreAnnotatedBySpecifiedTerm() {
        final SearchParam param = new SearchParam(
            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_as_relationship),
            Set.of(annotatingTerm.getUri().toString()),
            MatchType.IRI
        );

        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(param), Constants.DEFAULT_PAGE_SPEC);

        assertFalse(result.isEmpty());
        // Both termA (subject) and termB (object) should be in the results
        final Set<URI> resultUris = result.stream().map(FacetedSearchResult::getUri).collect(java.util.stream.Collectors.toSet());
        assertThat(resultUris, hasItem(termA.getUri()));
        assertThat(resultUris, hasItem(termB.getUri()));
    }

    @Test
    void facetedTermSearchDoesNotFindTermsWhoseRelationshipsAreNotAnnotated() {
        final SearchParam param = new SearchParam(
            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_as_relationship),
            Set.of(annotatingTerm.getUri().toString()),
            MatchType.IRI
        );

        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(param), Constants.DEFAULT_PAGE_SPEC);

        final Set<URI> resultUris = result.stream().map(FacetedSearchResult::getUri).collect(java.util.stream.Collectors.toSet());
        assertThat(resultUris, org.hamcrest.Matchers.not(hasItem(termC.getUri())));
    }

    @Test
    void facetedTermSearchCombinesRelationshipAnnotationWithOtherSearchParameters() {
        final SearchParam relationshipParam = new SearchParam(
            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_as_relationship),
            Set.of(annotatingTerm.getUri().toString()),
            MatchType.IRI
        );

        final SearchParam vocabularyParam = new SearchParam(
            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku),
            Set.of(vocabulary.getUri().toString()),
            MatchType.IRI
        );

        final List<FacetedSearchResult> result = sut.facetedTermSearch(
            Set.of(relationshipParam, vocabularyParam),
            Constants.DEFAULT_PAGE_SPEC
        );

        assertFalse(result.isEmpty());
        result.forEach(r -> assertEquals(vocabulary.getUri(), r.getVocabulary()));
    }

    @Test
    void facetedTermSearchReturnsEmptyResultWhenNoRelationshipsAnnotatedBySpecifiedTerm() {
        final Term otherTerm = Generator.generateTermWithId();
        setPrimaryLabel(otherTerm, "Other Term");
        otherTerm.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(otherTerm, descriptorFactory.termDescriptor(otherTerm)));

        final SearchParam param = new SearchParam(
            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_as_relationship),
            Set.of(otherTerm.getUri().toString()),
            MatchType.IRI
        );

        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(param), Constants.DEFAULT_PAGE_SPEC);

        assertEquals(0, result.size());
    }
}

