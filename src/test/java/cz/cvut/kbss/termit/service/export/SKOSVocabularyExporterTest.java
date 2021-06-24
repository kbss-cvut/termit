package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SKOSVocabularyExporterTest extends VocabularyExporterTestBase {

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private Configuration config;

    @Autowired
    private SKOSVocabularyExporter sut;

    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void supportsReturnsTrueForTurtleMediaType() {
        assertTrue(sut.supports(Constants.Turtle.MEDIA_TYPE));
    }

    @Test
    void supportsReturnsFalseForNonRdfSerializationMediaType() {
        assertFalse(sut.supports(MediaType.APPLICATION_JSON_VALUE));
    }

    @Test
    void exportVocabularyGlossaryExportsGlossaryInfo() throws IOException {
        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model, hasItem(vf
                .createStatement(glossaryIri(vocabulary), RDF.TYPE, SKOS.CONCEPT_SCHEME)));
        assertThat(model, hasItem(vf
                .createStatement(glossaryIri(vocabulary), RDF.TYPE, OWL.ONTOLOGY)));
        assertThat(model, hasItem(vf
                .createStatement(glossaryIri(vocabulary), DCTERMS.TITLE,
                        vf.createLiteral(vocabulary.getLabel(), lang()))));
    }

    private Model loadAsModel(TypeAwareResource result) throws IOException {
        final RDFParser parser = Rio.createParser(RDFFormat.TURTLE);
        Model model = new LinkedHashModel();
        parser.setRDFHandler(new StatementCollector(model));
        parser.parse(result.getInputStream(), "");
        return model;
    }

    private IRI glossaryIri(Vocabulary vocabulary) {
        return vf.createIRI(vocabulary.getGlossary().getUri().toString());
    }

    private String lang() {
        return config.getPersistence().getLanguage();
    }

    @Test
    void exportVocabularyGlossaryExportsImportsOfOtherGlossariesAsOWLImports() throws IOException {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Collections.singleton(anotherVocabulary.getUri()));
        transactional(() -> {
            em.persist(anotherVocabulary, descriptorFactory.vocabularyDescriptor(anotherVocabulary));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                hasItem(vf.createStatement(glossaryIri(vocabulary), OWL.IMPORTS, glossaryIri(anotherVocabulary))));
    }

    @Test
    void exportVocabularyGlossaryExportsProvenanceRightsStatusVersionNamespace() throws IOException {
        insertAdditionalGlossaryData();

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertTrue(model.contains(glossaryIri(vocabulary), OWL.VERSIONIRI, null));
        assertTrue(model.contains(glossaryIri(vocabulary), DCTERMS.CREATOR, null));
        assertTrue(model.contains(glossaryIri(vocabulary), DCTERMS.CREATED, null));
        assertTrue(model.contains(glossaryIri(vocabulary), DCTERMS.RIGHTS, null));
        assertTrue(model.contains(glossaryIri(vocabulary),
                vf.createIRI("http://purl.org/vocab/vann/preferredNamespacePrefix"), null));
        assertTrue(model.contains(glossaryIri(vocabulary),
                vf.createIRI("http://purl.org/vocab/vann/preferredNamespaceUri"), null));
        assertTrue(model.contains(glossaryIri(vocabulary), vf.createIRI("http://purl.org/ontology/bibo/status"), null));
    }

    private void insertAdditionalGlossaryData() {
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                conn.begin();
                conn.add(glossaryIri(vocabulary), OWL.VERSIONIRI, glossaryIri(vocabulary));
                conn.add(glossaryIri(vocabulary), vf.createIRI("http://purl.org/vocab/vann/preferredNamespacePrefix"),
                        vf.createLiteral("termit:"));
                conn.add(glossaryIri(vocabulary), vf.createIRI("http://purl.org/vocab/vann/preferredNamespaceUri"),
                        vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.ONTOLOGY_IRI_termit));
                conn.add(glossaryIri(vocabulary), DCTERMS.RIGHTS,
                        vf.createIRI("https://creativecommons.org/licenses/by-nc-nd/4.0"));
                conn.add(glossaryIri(vocabulary), vf.createIRI("http://purl.org/ontology/bibo/status"),
                        vf.createLiteral("Specifikace", "cs"));
                conn.commit();
            }
        });
    }

    @Test
    void exportVocabularyGlossaryExportsTermsInVocabularyAsSKOSConceptsInScheme() throws Exception {
        final List<Term> terms = generateTerms();
        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        for (Term t : terms) {
            assertThat(model,
                    hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), RDF.TYPE, SKOS.CONCEPT)));
            assertThat(model, hasItem(
                    vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.IN_SCHEME, glossaryIri(vocabulary))));
        }
    }

    @Test
    void exportVocabularyGlossaryExportsTermsWithSKOSPropertiesAndDcSource() throws Exception {
        final List<Term> terms = generateTerms();
        final Term withAltLabels = terms.get(Generator.randomIndex(terms));
        insertAltLabels(withAltLabels);
        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        for (Term t : terms) {
            t.getLabel().getValue().forEach((lang, val) -> assertThat(model,
                    hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.PREF_LABEL,
                            vf.createLiteral(val, lang)))));
            if (t.getAltLabels() != null && !t.getAltLabels().isEmpty()) {
                t.getAltLabels().forEach(src -> src.getValue().forEach((lang, val) -> assertThat(model,
                        hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.ALT_LABEL,
                                vf.createLiteral(val, lang))))));

            }
            if (t.getHiddenLabels() != null && !t.getHiddenLabels().isEmpty()) {
                t.getHiddenLabels().forEach(src -> src.getValue().forEach((lang, val) -> assertThat(model,
                        hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.HIDDEN_LABEL,
                                vf.createLiteral(val, lang))))));

            }
            t.getDefinition().getValue().forEach((lang, val) -> assertThat(model,
                    hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.DEFINITION,
                            vf.createLiteral(val, lang)))));
            if (t.getSources() != null && !t.getSources().isEmpty()) {
                t.getSources().forEach(src -> assertThat(model,
                        hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), DCTERMS.SOURCE,
                                vf.createLiteral(src)))));

            }
        }
        assertThat(model.filter(vf.createIRI(withAltLabels.getUri().toString()), SKOS.ALT_LABEL, null), not(empty()));
    }

    private void insertAltLabels(Term t) {
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                conn.begin();
                final IRI iri = vf.createIRI(t.getUri().toString());
                conn.add(iri, SKOS.ALT_LABEL, vf.createLiteral("alternativní název", "cs"));
                conn.add(iri, SKOS.ALT_LABEL, vf.createLiteral("alternativer Name", "de"));
                conn.commit();
            }
        });
    }

    @Test
    void exportVocabularyGlossaryExportsHierarchicalStructureOfTerms() throws Exception {
        final List<Term> terms = generateTerms();
        final Term withParent = terms.get(1);
        withParent.addParentTerm(terms.get(0));
        // This is normally inferred
        withParent.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(withParent, descriptorFactory.termDescriptor(withParent)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        for (Term t : terms) {
            if (t.equals(withParent)) {
                withParent.getParentTerms()
                          .forEach(pt -> assertThat(model,
                                  hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.BROADER,
                                          vf.createIRI(pt.getUri().toString())))));

            } else {
                assertThat(model, hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.TOP_CONCEPT_OF,
                        glossaryIri(vocabulary))));
            }
        }
    }

    @Test
    void exportVocabularyGlossaryExportsRelatedTerms() throws Exception {
        final List<Term> terms = generateTerms();
        final Term withRelated = terms.get(0);
        final Term related = terms.get(terms.size() - 1);
        withRelated.setProperties(Collections
                .singletonMap(SKOS.RELATED.stringValue(), Collections.singleton(related.getUri().toString())));
        // This is normally inferred
        withRelated.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(withRelated, descriptorFactory.termDescriptor(withRelated)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model, hasItem(vf.createStatement(vf.createIRI(withRelated.getUri().toString()), SKOS.RELATED,
                vf.createIRI(related.getUri().toString()))));
    }

    @Test
    void exportVocabularyGlossaryExportsTermAsTopConceptIfParentIsInDifferentVocabulary() throws Exception {
        final List<Term> terms = generateTerms();
        final Term affectedTerm = terms.get(Generator.randomIndex(terms));
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final Term parentFromAnother = Generator.generateTermWithId();
        parentFromAnother.setVocabulary(anotherVocabulary.getUri());
        anotherVocabulary.getGlossary().addRootTerm(parentFromAnother);
        affectedTerm.addParentTerm(parentFromAnother);
        vocabulary.setImportedVocabularies(Collections.singleton(anotherVocabulary.getUri()));
        // This is normally inferred
        affectedTerm.setVocabulary(vocabulary.getUri());
        transactional(() -> {
            em.persist(anotherVocabulary, descriptorFactory.vocabularyDescriptor(anotherVocabulary));
            em.persist(parentFromAnother, descriptorFactory.termDescriptor(parentFromAnother));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.merge(affectedTerm, descriptorFactory.termDescriptor(affectedTerm));
        });

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                hasItem(vf.createStatement(vf.createIRI(affectedTerm.getUri().toString()), SKOS.TOP_CONCEPT_OF,
                        glossaryIri(vocabulary))));
    }

    @Test
    void exportVocabularyGlossaryExportsCustomTypeAsSuperType() throws Exception {
        final List<Term> terms = generateTerms();
        final Term typed = terms.get(Generator.randomIndex(terms));
        final String type = "http://onto.fel.cvut.cz/ontologies/ufo/object-type";
        typed.addType(type);
        // This is normally inferred
        typed.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(typed, descriptorFactory.termDescriptor(typed)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                hasItem(vf.createStatement(vf.createIRI(typed.getUri().toString()), RDF.TYPE,
                        vf.createIRI(type))));
    }

    @Test
    void exportVocabularyGlossaryExportsSuperClassesAsBroader() throws Exception {
        final List<Term> terms = generateTerms();
        final Term rdfsSubclass = terms.get(0);
        final String supertype = Generator.generateUri().toString();
        rdfsSubclass.setProperties(
                Collections.singletonMap(RDFS.SUBCLASSOF.stringValue(), Collections.singleton(supertype)));
        // This is normally inferred
        rdfsSubclass.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(rdfsSubclass, descriptorFactory.termDescriptor(rdfsSubclass)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                hasItem(vf.createStatement(vf.createIRI(rdfsSubclass.getUri().toString()), SKOS.BROADER,
                        vf.createIRI(supertype))));
    }

    @Test
    void exportVocabularyGlossaryExportsPartOfAsBroader() throws Exception {
        final List<Term> terms = generateTerms();
        final Term partOf = terms.get(0);
        final Term hasPart = terms.get(1);
        hasPart.setProperties(
                Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_has_part,
                        Collections.singleton(partOf.getUri().toString())));
        // This is normally inferred
        hasPart.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(hasPart, descriptorFactory.termDescriptor(hasPart)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                hasItem(vf.createStatement(vf.createIRI(partOf.getUri().toString()), SKOS.BROADER,
                        vf.createIRI(hasPart.getUri().toString()))));
    }

    @Test
    void exportVocabularyGlossaryExportsParticipationAsBroader() throws Exception {
        final List<Term> terms = generateTerms();
        final Term participant = terms.get(0);
        final Term parent = terms.get(1);
        parent.setProperties(
                Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_has_participant,
                        Collections.singleton(participant.getUri().toString())));
        // This is normally inferred
        parent.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(parent, descriptorFactory.termDescriptor(parent)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                hasItem(vf.createStatement(vf.createIRI(participant.getUri().toString()), SKOS.BROADER,
                        vf.createIRI(parent.getUri().toString()))));
    }

    @Test
    void exportVocabularyGlossarySkipsOwlConstructsUsedToClassifyTerms() throws Exception {
        final List<Term> terms = generateTerms();
        final Term withOwl = terms.get(Generator.randomIndex(terms));
        withOwl.addType(OWL.CLASS.stringValue());
        // This is normally inferred
        withOwl.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(withOwl, descriptorFactory.termDescriptor(withOwl)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                not(hasItem(vf.createStatement(vf.createIRI(withOwl.getUri().toString()), SKOS.BROADER, OWL.CLASS))));
    }

    @Test
    void exportVocabularyGlossarySkipsOwlConstructsUsedAsTermSuperTypes() throws Exception {
        final List<Term> terms = generateTerms();
        final Term withOwl = terms.get(Generator.randomIndex(terms));
        withOwl.setProperties(Collections
                .singletonMap(RDFS.SUBCLASSOF.stringValue(), Collections.singleton(OWL.OBJECTPROPERTY.stringValue())));
        // This is normally inferred
        withOwl.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(withOwl, descriptorFactory.termDescriptor(withOwl)));

        final TypeAwareResource result = sut.exportVocabularyGlossary(vocabulary);
        final Model model = loadAsModel(result);
        assertThat(model,
                not(hasItem(vf.createStatement(vf.createIRI(withOwl.getUri().toString()), SKOS.BROADER,
                        OWL.OBJECTPROPERTY))));
    }
}
