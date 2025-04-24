/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.environment.Environment.getPrimaryLabel;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SKOSVocabularyExporterTest extends BaseServiceTestRunner {

    private static final IRI[] REFERENCING_PROPERTIES = {SKOS.BROAD_MATCH, SKOS.EXACT_MATCH, SKOS.RELATED_MATCH};

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private Configuration config;

    @Autowired
    @Qualifier("skos-turtle")
    private SKOSVocabularyExporter sut;

    Vocabulary vocabulary;

    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        final User author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> {
            em.persist(author);
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            final PersistChangeRecord record = Generator.generatePersistChange(vocabulary);
            record.setAuthor(author);
            em.persist(record);
        });
    }

    @Test
    void supportsReturnsTrueForTurtleMediaType() {
        assertTrue(sut.supports(Constants.MediaType.TURTLE));
    }

    @Test
    void supportsReturnsFalseForNonRdfSerializationMediaType() {
        assertFalse(sut.supports(MediaType.APPLICATION_JSON_VALUE));
    }

    @Test
    void exportGlossaryExportsGlossaryInfo() throws IOException {
        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model, hasItem(vf.createStatement(glossaryIri(vocabulary), RDF.TYPE, SKOS.CONCEPT_SCHEME)));
        assertThat(model, hasItem(vf.createStatement(glossaryIri(vocabulary), RDF.TYPE, OWL.ONTOLOGY)));
        assertThat(model, hasItem(vf.createStatement(glossaryIri(vocabulary), DCTERMS.TITLE,
                vf.createLiteral(getPrimaryLabel(vocabulary), lang()))));
        assertThat(model, hasItem(vf.createStatement(glossaryIri(vocabulary), DCTERMS.DESCRIPTION,
                                                     vf.createLiteral(vocabulary.getDescription().get(lang()), lang()))));
    }

    private static ExportConfig exportConfig() {
        return new ExportConfig(ExportType.SKOS, ExportFormat.TURTLE.getMediaType());
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
    void exportGlossaryExportsImportsOfOtherGlossariesAsOWLImports() throws IOException {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Collections.singleton(anotherVocabulary.getUri()));
        transactional(() -> {
            em.persist(anotherVocabulary, descriptorFactory.vocabularyDescriptor(anotherVocabulary));
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   hasItem(vf.createStatement(glossaryIri(vocabulary), OWL.IMPORTS, glossaryIri(anotherVocabulary))));
    }

    @Test
    void exportGlossaryExportsProvenanceRightsStatusVersionNamespace() throws IOException {
        insertAdditionalGlossaryData();

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
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
                conn.add(glossaryIri(vocabulary), OWL.VERSIONIRI, glossaryIri(vocabulary),
                         vf.createIRI(vocabulary.getUri().toString()));
                conn.add(glossaryIri(vocabulary), vf.createIRI("http://purl.org/vocab/vann/preferredNamespacePrefix"),
                         vf.createLiteral("termit:"), vf.createIRI(vocabulary.getUri().toString()));
                conn.add(glossaryIri(vocabulary), vf.createIRI("http://purl.org/vocab/vann/preferredNamespaceUri"),
                         vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.ONTOLOGY_IRI_TERMIT),
                         vf.createIRI(vocabulary.getUri().toString()));
                conn.add(glossaryIri(vocabulary), DCTERMS.RIGHTS,
                         vf.createIRI("https://creativecommons.org/licenses/by-nc-nd/4.0"),
                         vf.createIRI(vocabulary.getUri().toString()));
                conn.add(glossaryIri(vocabulary), vf.createIRI("http://purl.org/ontology/bibo/status"),
                         vf.createLiteral("Specifikace", "cs"), vf.createIRI(vocabulary.getUri().toString()));
                conn.commit();
            }
        });
    }

    @Test
    void exportGlossaryExportsTermsInVocabularyAsSKOSConceptsInScheme() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        for (Term t : terms) {
            assertThat(model,
                       hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), RDF.TYPE, SKOS.CONCEPT)));
            assertThat(model, hasItem(
                    vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.IN_SCHEME, glossaryIri(vocabulary))));
        }
    }

    List<Term> generateTerms(Vocabulary target) {
        final List<Term> terms = new ArrayList<>(10);
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final Term term = Generator.generateTermWithId();
            terms.add(term);
            term.setGlossary(target.getGlossary().getUri());
        }
        target.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            em.merge(target.getGlossary(), descriptorFactory.glossaryDescriptor(target));
            terms.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(target)));
            terms.forEach(t -> Generator.addTermInVocabularyRelationship(t, target.getUri(), em));
        });
        return terms;
    }

    @Test
    void exportGlossaryExportsTermsWithSKOSPropertiesAndDcSource() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term withAltLabels = terms.get(Generator.randomIndex(terms));
        insertAltLabels(withAltLabels);
        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        for (Term t : terms) {
            t.getLabel().getValue().forEach((lang, val) -> assertThat(model,
                                                                      hasItem(vf.createStatement(
                                                                              vf.createIRI(t.getUri().toString()),
                                                                              SKOS.PREF_LABEL,
                                                                              vf.createLiteral(val, lang)))));
            if (t.getAltLabels() != null && !t.getAltLabels().isEmpty()) {
                t.getAltLabels().forEach(src -> src.getValue().forEach((lang, val) -> assertThat(model,
                                                                                                 hasItem(vf.createStatement(
                                                                                                         vf.createIRI(
                                                                                                                 t.getUri()
                                                                                                                  .toString()),
                                                                                                         SKOS.ALT_LABEL,
                                                                                                         vf.createLiteral(
                                                                                                                 val,
                                                                                                                 lang))))));

            }
            if (t.getHiddenLabels() != null && !t.getHiddenLabels().isEmpty()) {
                t.getHiddenLabels().forEach(src -> src.getValue().forEach((lang, val) -> assertThat(model,
                                                                                                    hasItem(vf.createStatement(
                                                                                                            vf.createIRI(
                                                                                                                    t.getUri()
                                                                                                                     .toString()),
                                                                                                            SKOS.HIDDEN_LABEL,
                                                                                                            vf.createLiteral(
                                                                                                                    val,
                                                                                                                    lang))))));

            }
            t.getDefinition().getValue().forEach((lang, val) -> assertThat(model,
                                                                           hasItem(vf.createStatement(
                                                                                   vf.createIRI(t.getUri().toString()),
                                                                                   SKOS.DEFINITION,
                                                                                   vf.createLiteral(val, lang)))));
            if (t.getSources() != null && !t.getSources().isEmpty()) {
                t.getSources().forEach(src -> assertThat(model,
                                                         hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()),
                                                                                    DCTERMS.SOURCE,
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
                conn.add(iri, SKOS.ALT_LABEL, vf.createLiteral("alternativní název", "cs"),
                         vf.createIRI(vocabulary.getUri().toString()));
                conn.add(iri, SKOS.ALT_LABEL, vf.createLiteral("alternativer Name", "de"),
                         vf.createIRI(vocabulary.getUri().toString()));
                conn.commit();
            }
        });
    }

    @Test
    void exportGlossaryExportsHierarchicalStructureOfTerms() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term withParent = terms.get(1);
        withParent.addParentTerm(terms.get(0));
        // This is normally inferred
        withParent.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(withParent, descriptorFactory.termDescriptor(withParent)));

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        for (Term t : terms) {
            if (t.equals(withParent)) {
                withParent.getParentTerms()
                          .forEach(pt -> assertThat(model,
                                                    hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()),
                                                                               SKOS.BROADER,
                                                                               vf.createIRI(pt.getUri().toString())))));

            } else {
                assertThat(model, hasItem(vf.createStatement(vf.createIRI(t.getUri().toString()), SKOS.TOP_CONCEPT_OF,
                                                             glossaryIri(vocabulary))));
            }
        }
    }

    @Test
    void exportGlossaryExportsRelatedTerms() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term withRelated = terms.get(0);
        final Term related = terms.get(terms.size() - 1);
        withRelated.setRelated(Set.of(new TermInfo(related)));
        // This is normally inferred
        withRelated.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(withRelated, descriptorFactory.termDescriptor(withRelated)));

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model, hasItem(vf.createStatement(vf.createIRI(withRelated.getUri().toString()), SKOS.RELATED,
                                                     vf.createIRI(related.getUri().toString()))));
    }

    @Test
    void exportGlossaryExportsTermAsTopConceptIfParentIsInDifferentVocabulary() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
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

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   hasItem(vf.createStatement(vf.createIRI(affectedTerm.getUri().toString()), SKOS.TOP_CONCEPT_OF,
                                              glossaryIri(vocabulary))));
    }

    @Test
    void exportGlossaryExportsCustomTypeAsSuperType() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term typed = terms.get(Generator.randomIndex(terms));
        final String type = "http://onto.fel.cvut.cz/ontologies/ufo/object-type";
        typed.addType(type);
        // This is normally inferred
        typed.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(typed, descriptorFactory.termDescriptor(typed)));

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   hasItem(vf.createStatement(vf.createIRI(typed.getUri().toString()), RDF.TYPE,
                                              vf.createIRI(type))));
    }

    @Test
    void exportGlossaryExportsSuperClassesAsBroader() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term rdfsSubclass = terms.get(0);
        final String supertype = Generator.generateUri().toString();
        transactional(() -> {
            insertPropertyAssertion(rdfsSubclass, RDFS.SUBCLASSOF.stringValue(), supertype);
            // Simulate inference
            Generator.addTermInVocabularyRelationship(rdfsSubclass, vocabulary.getUri(), em);
        });

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   hasItem(vf.createStatement(vf.createIRI(rdfsSubclass.getUri().toString()), SKOS.BROADER,
                                              vf.createIRI(supertype))));
    }

    private void insertPropertyAssertion(Term subject, String property, String value) {
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.add(vf.createIRI(subject.getUri().toString()), vf.createIRI(property), vf.createIRI(value),
                     vf.createIRI(vocabulary.getUri().toString()));
        }
    }

    @Test
    void exportGlossaryExportsPartOfAsBroader() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term partOf = terms.get(0);
        final Term hasPart = terms.get(1);
        transactional(() -> {
            insertPropertyAssertion(hasPart, cz.cvut.kbss.termit.util.Vocabulary.s_p_has_part,
                                    partOf.getUri().toString());
            // Simulate inference
            Generator.addTermInVocabularyRelationship(hasPart, vocabulary.getUri(), em);
        });

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   hasItem(vf.createStatement(vf.createIRI(partOf.getUri().toString()), SKOS.BROADER,
                                              vf.createIRI(hasPart.getUri().toString()))));
    }

    @Test
    void exportGlossaryExportsParticipationAsBroader() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term participant = terms.get(0);
        final Term parent = terms.get(1);
        transactional(() -> {
            insertPropertyAssertion(parent, cz.cvut.kbss.termit.util.Vocabulary.s_p_has_participant,
                                    participant.getUri().toString());
            // Simulate inference
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
        });

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   hasItem(vf.createStatement(vf.createIRI(participant.getUri().toString()), SKOS.BROADER,
                                              vf.createIRI(parent.getUri().toString()))));
    }

    @Test
    void exportGlossarySkipsOwlConstructsUsedToClassifyTerms() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term withOwl = terms.get(Generator.randomIndex(terms));
        withOwl.addType(OWL.CLASS.stringValue());
        // This is normally inferred
        withOwl.setVocabulary(vocabulary.getUri());
        transactional(() -> em.merge(withOwl, descriptorFactory.termDescriptor(withOwl)));

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   not(hasItem(
                           vf.createStatement(vf.createIRI(withOwl.getUri().toString()), SKOS.BROADER, OWL.CLASS))));
    }

    @Test
    void exportGlossarySkipsOwlConstructsUsedAsTermSuperTypes() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Term withOwl = terms.get(Generator.randomIndex(terms));
        transactional(() -> {
            insertPropertyAssertion(withOwl, RDFS.SUBCLASSOF.stringValue(), OWL.OBJECTPROPERTY.stringValue());
            // Simulate inference
            Generator.addTermInVocabularyRelationship(withOwl, vocabulary.getUri(), em);
        });

        final TypeAwareResource result = sut.exportGlossary(vocabulary, exportConfig());
        final Model model = loadAsModel(result);
        assertThat(model,
                   not(hasItem(vf.createStatement(vf.createIRI(withOwl.getUri().toString()), SKOS.BROADER,
                                                  OWL.OBJECTPROPERTY))));
    }

    @Test
    void exportGlossaryWithReferencesIncludesExternalTermsReferencedViaSpecifiedProperties() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(anotherVocabulary, descriptorFactory.vocabularyDescriptor(anotherVocabulary)));
        final List<Term> externalTerms = generateTerms(anotherVocabulary);
        final IRI property = REFERENCING_PROPERTIES[Generator.randomIndex(REFERENCING_PROPERTIES)];
        final Set<Term> referencedExternal = generateReferences(terms, externalTerms, property);

        final ExportConfig config = new ExportConfig(ExportType.SKOS_WITH_REFERENCES,
                                                     ExportFormat.TURTLE.getMediaType(),
                                                     Collections.singleton(property.stringValue()));
        final TypeAwareResource result = sut.exportGlossary(vocabulary, config);
        final Model model = loadAsModel(result);
        referencedExternal.forEach(rt -> assertThat(model,
                                                    hasItem(vf.createStatement(vf.createIRI(rt.getUri().toString()),
                                                                               RDF.TYPE, SKOS.CONCEPT))));
    }

    private Set<Term> generateReferences(List<Term> terms, List<Term> externalTerms, IRI property) {
        final Set<Term> referencedTerms = new HashSet<>();
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection con = repo.getConnection()) {
                con.begin();
                final IRI ctx = vf.createIRI(vocabulary.getUri().toString());
                terms.forEach(t -> {
                    final Term referenced = externalTerms.get(Generator.randomIndex(externalTerms));
                    con.add(vf.createIRI(t.getUri().toString()), property, vf.createIRI(referenced.getUri()
                                                                                                  .toString()), ctx);
                    referencedTerms.add(referenced);
                });
                con.commit();
            }
        });
        return referencedTerms;
    }

    @Test
    void exportGlossaryWithReferencesExportsGlossariesOfReferencedTerms() throws Exception {
        final List<Term> terms = generateTerms(vocabulary);
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(anotherVocabulary, descriptorFactory.vocabularyDescriptor(anotherVocabulary)));
        final List<Term> externalTerms = generateTerms(anotherVocabulary);
        final IRI property = REFERENCING_PROPERTIES[Generator.randomIndex(REFERENCING_PROPERTIES)];
        generateReferences(terms, externalTerms, property);

        final ExportConfig config = new ExportConfig(ExportType.SKOS_WITH_REFERENCES,
                                                     ExportFormat.TURTLE.getMediaType(),
                                                     Collections.singleton(property.stringValue()));
        final TypeAwareResource result = sut.exportGlossary(vocabulary, config);
        final Model model = loadAsModel(result);
        assertThat(model, hasItem(vf.createStatement(vf.createIRI(anotherVocabulary.getGlossary().getUri()
                                                                                   .toString()), RDF.TYPE,
                                                     SKOS.CONCEPT_SCHEME)));
    }
}
