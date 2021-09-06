/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AnnotationGenerationException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Model;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.assignment.TermFileOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.web.WebAppConfiguration;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.environment.Environment.loadFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

// Needed for the request-scoped occurrence resolver bean to work
@WebAppConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AnnotationGeneratorTest extends BaseServiceTestRunner {

    private static final URI TERM_ID = URI.create("http://onto.fel.cvut.cz/ontologies/mpp/domains/uzemni-plan");
    private static final URI TERM_TWO_ID = URI
            .create("http://onto.fel.cvut.cz/ontologies/mpp/domains/uzemni-plan-praha");

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private TermOccurrenceDao termOccurrenceDao;

    @Autowired
    private Configuration config;

    @Autowired
    private TermDao termDao;

    @Autowired
    private AnnotationGenerator sut;

    private cz.cvut.kbss.termit.model.Vocabulary vocabulary;
    private Descriptor vocabDescriptor;
    private cz.cvut.kbss.termit.model.resource.Document document;
    private File file;
    private String fileLocation;

    private Term term;
    private Term termTwo;

    @BeforeEach
    void setUp() {
        this.term = new Term();
        term.setUri(TERM_ID);
        term.setLabel(MultilingualString.create("Územní plán", cz.cvut.kbss.termit.environment.Environment.LANGUAGE));
        this.termTwo = new Term();
        termTwo.setUri(TERM_TWO_ID);
        termTwo.setLabel(MultilingualString
                .create("Územní plán hlavního města Prahy", cz.cvut.kbss.termit.environment.Environment.LANGUAGE));
        final User author = Generator.generateUserWithId();
        this.vocabulary = new cz.cvut.kbss.termit.model.Vocabulary();
        vocabulary.setLabel("Test Vocabulary");
        vocabulary.setGlossary(new Glossary());
        vocabulary.setModel(new Model());
        vocabulary.setUri(Generator.generateUri());
        this.document = new cz.cvut.kbss.termit.model.resource.Document();
        document.setLabel("metropolitan-plan");
        document.setUri(Generator.generateUri());
        document.setVocabulary(vocabulary.getUri());
        vocabulary.setDocument(document);
        vocabulary.getGlossary().addRootTerm(term);
        vocabulary.getGlossary().addRootTerm(termTwo);
        this.vocabDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        this.file = new File();
        file.setUri(Generator.generateUri());
        file.setLabel("rdfa-simple.html");
        file.setDocument(document);
        cz.cvut.kbss.termit.environment.Environment.setCurrentUser(author);
        document.addFile(file);
        transactional(() -> {
            em.persist(author);
            em.persist(vocabulary, vocabDescriptor);
            em.persist(document, descriptorFactory.documentDescriptor(vocabulary));
            em.persist(file, descriptorFactory.documentDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(termTwo, descriptorFactory.termDescriptor(vocabulary));
        });
        enableRdfsInference(em);
    }

    private void generateFile() throws Exception {
        final java.io.File folder = Files.createTempDirectory("termit").toFile();
        folder.deleteOnExit();
        final String docFolderName = vocabulary.getDocument().getDirectoryName();
        final java.io.File docDir = new java.io.File(folder.getAbsolutePath() + java.io.File.separator + docFolderName);
        Files.createDirectories(docDir.toPath());
        docDir.deleteOnExit();
        final java.io.File f = new java.io.File(
                folder.getAbsolutePath() + java.io.File.separator + docFolderName + java.io.File.separator +
                        file.getLabel());
        Files.createFile(f.toPath());
        f.deleteOnExit();
        this.fileLocation = f.getAbsolutePath();
        config.getFile().setStorage(folder.getAbsolutePath());
    }

    @Test
    void generateAnnotationsCreatesTermOccurrenceForTermFoundInContentDocument() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final List<TermOccurrence> result = findAllOccurrencesOf(term);
        assertEquals(1, result.size());
    }

    private List<TermOccurrence> findAllOccurrencesOf(Term term) {
        return em.createQuery("SELECT DISTINCT to FROM TermOccurrence to WHERE to.term = :term", TermOccurrence.class)
                 .setParameter("term", term).getResultList();
    }

    @Test
    void generateAnnotationsSkipsElementsWithUnsupportedType() throws Exception {
        final InputStream content = changeAnnotationType(loadFile("data/rdfa-simple.html"));
        generateFile();
        sut.generateAnnotations(content, file);
        assertTrue(findAllOccurrencesOf(term).isEmpty());
    }

    private InputStream changeAnnotationType(InputStream content) throws Exception {
        final Document doc = Jsoup.parse(content, StandardCharsets.UTF_8.name(), "");
        final Elements element = doc.getElementsByAttribute(Constants.RDFa.ABOUT);
        assert element.size() == 1;
        element.attr(Constants.RDFa.TYPE, cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik);

        return new ByteArrayInputStream(doc.toString().getBytes(StandardCharsets.UTF_8.name()));
    }

    @Test
    void generateAnnotationsThrowsAnnotationGenerationExceptionForUnsupportedFileType() throws Exception {
        final InputStream content = loadFile("application.yml");
        file.setLabel(generateIncompatibleFile());
        final AnnotationGenerationException ex = assertThrows(AnnotationGenerationException.class,
                () -> sut.generateAnnotations(content, file));
        assertThat(ex.getMessage(), containsString("Unsupported type of file"));
    }

    private String generateIncompatibleFile() throws Exception {
        final String tempDir = System.getProperty("java.io.tmpdir");
        config.getFile().setStorage(tempDir);
        final java.io.File docDir = new java.io.File(tempDir + java.io.File.separator +
                document.getDirectoryName());
        Files.createDirectory(docDir.toPath());
        docDir.deleteOnExit();
        final java.io.File content = Files.createTempFile(docDir.toPath(), "test", ".txt").toFile();
        content.deleteOnExit();
        return content.getName();
    }

    @Test
    void generateAnnotationsResolvesOverlappingAnnotations() throws Exception {
        final InputStream content = loadFile("data/rdfa-overlapping.html");
        file.setLabel("rdfa-overlapping.html");
        generateFile();
        sut.generateAnnotations(content, file);
        assertEquals(1, findAllOccurrencesOf(term).size());
        assertEquals(1, findAllOccurrencesOf(termTwo).size());
    }

    @Test
    void generateAnnotationsThrowsAnnotationGenerationExceptionForUnknownTermIdentifier() throws Exception {
        final InputStream content = setUnknownTermIdentifier(loadFile("data/rdfa-simple.html"));
        final AnnotationGenerationException ex = assertThrows(AnnotationGenerationException.class,
                () -> sut.generateAnnotations(content, file));
        assertThat(ex.getMessage(), containsString("Term with id "));
        assertThat(ex.getMessage(), containsString("not found"));
    }

    private InputStream setUnknownTermIdentifier(InputStream content) throws Exception {
        final Document doc = Jsoup.parse(content, StandardCharsets.UTF_8.name(), "");
        final Elements element = doc.getElementsByAttribute(Constants.RDFa.ABOUT);
        assert element.size() == 1;
        element.attr(Constants.RDFa.RESOURCE, Generator.generateUri().toString());

        return new ByteArrayInputStream(doc.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void generateAnnotationsHandlesLargerDocumentAnalysis() throws Exception {
        final Term mp = new Term();
        mp.setLabel(MultilingualString
                .create("Metropolitní plán", cz.cvut.kbss.termit.environment.Environment.LANGUAGE));
        mp.setUri(URI.create("http://test.org/pojem/metropolitni-plan"));
        final Term ma = new Term();
        ma.setLabel(MultilingualString
                .create("Správní území Prahy", cz.cvut.kbss.termit.environment.Environment.LANGUAGE));
        ma.setUri(URI.create("http://test.org/pojem/spravni-uzemi-prahy"));
        final Term area = new Term();
        area.setLabel(MultilingualString.create("Území", cz.cvut.kbss.termit.environment.Environment.LANGUAGE));
        area.setUri(URI.create("http://test.org/pojem/uzemi"));
        vocabulary.getGlossary().addRootTerm(mp);
        vocabulary.getGlossary().addRootTerm(ma);
        vocabulary.getGlossary().addRootTerm(area);
        transactional(() -> {
            em.persist(mp, descriptorFactory.termDescriptor(vocabulary));
            em.persist(ma, descriptorFactory.termDescriptor(vocabulary));
            em.persist(area, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });

        final InputStream content = loadFile("data/rdfa-large.html");
        file.setLabel("rdfa-large.html");
        generateFile();
        sut.generateAnnotations(content, file);
        assertFalse(findAllOccurrencesOf(mp).isEmpty());
        assertFalse(findAllOccurrencesOf(ma).isEmpty());
        assertFalse(findAllOccurrencesOf(area).isEmpty());
    }

    @Test
    void generateAnnotationsAddsThemSuggestedTypeToIndicateTheyShouldBeVerifiedByUser() throws Exception {
        final InputStream content = loadFile("data/rdfa-overlapping.html");
        file.setLabel("rdfa-overlapping.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final List<TermOccurrence> result = termOccurrenceDao.findAll();
        result.forEach(to -> assertTrue(
                to.getTypes().contains(cz.cvut.kbss.termit.util.Vocabulary.s_c_navrzeny_vyskyt_termu)));
        assertEquals(1, findAllOccurrencesOf(term).size());
        assertEquals(1, findAllOccurrencesOf(termTwo).size());
    }

    @Test
    void generateAnnotationsDoesNotAddTermsForSuggestedKeywords() throws Exception {
        final InputStream content = loadFile("data/rdfa-new-terms.html");
        file.setLabel("rdfa-new-terms.html");
        generateFile();
        final List<Term> origTerms = termDao.findAll();
        sut.generateAnnotations(content, file);
        final List<Term> resultTerms = termDao.findAll();
        assertEquals(origTerms.size(), resultTerms.size());
    }

    @Test
    void generateAnnotationsDoesNotModifyIncomingRdfWhenItContainsNewTermSuggestions() throws Exception {
        final InputStream content = loadFile("data/rdfa-new-terms.html");
        file.setLabel("rdfa-new-terms.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final Document originalDoc;
        try (final BufferedReader oldIn = new BufferedReader(
                new InputStreamReader(new FileInputStream(fileLocation)))) {
            final String originalContent = oldIn.lines().collect(Collectors.joining("\n"));
            originalDoc = Jsoup.parse(originalContent);
        }
        try (final BufferedReader newIn = new BufferedReader(
                new InputStreamReader(new FileInputStream(fileLocation)))) {
            final String currentContent = newIn.lines().collect(Collectors.joining("\n"));
            final Document currentDoc = Jsoup.parse(currentContent);
            assertTrue(originalDoc.hasSameValue(currentDoc));
        }
    }

    @Test
    void generateAnnotationsResolvesTermOccurrenceWhenItOverlapsWithNewTermSuggestion() throws Exception {
        final InputStream content = loadFile("data/rdfa-new-terms-overlapping.html");
        file.setLabel("rdfa-new-terms-overlapping.html");
        generateFile();
        final List<Term> origTerms = termDao.findAll();
        sut.generateAnnotations(content, file);
        final List<Term> resultTerms = termDao.findAll();
        assertEquals(origTerms.size(), resultTerms.size());
        final List<TermOccurrence> to = findAllOccurrencesOf(term);
        assertEquals(1, to.size());
    }

    @Test
    void generateAnnotationsSkipsRDFaAnnotationsWithoutResourceAndContent() throws Exception {
        final InputStream content = removeResourceAndContent(loadFile("data/rdfa-simple.html"));
        generateFile();
        sut.generateAnnotations(content, file);
        assertTrue(termOccurrenceDao.findAll().isEmpty());
    }

    private InputStream removeResourceAndContent(InputStream input) throws IOException {
        final Document doc = Jsoup.parse(input, StandardCharsets.UTF_8.name(), "");
        final Elements elements = doc.getElementsByAttribute(Constants.RDFa.ABOUT);
        elements.removeAttr(Constants.RDFa.RESOURCE);
        elements.removeAttr(Constants.RDFa.CONTENT);

        return new ByteArrayInputStream(doc.toString().getBytes());
    }

    @Test
    void generateAnnotationsSkipsAnnotationsWithEmptyResource() throws Exception {
        final InputStream content = setEmptyResource(loadFile("data/rdfa-simple.html"));
        generateFile();
        sut.generateAnnotations(content, file);
        assertTrue(termOccurrenceDao.findAll().isEmpty());
    }

    private InputStream setEmptyResource(InputStream input) throws IOException {
        final Document doc = Jsoup.parse(input, StandardCharsets.UTF_8.name(), "");
        final Elements elements = doc.getElementsByAttribute(Constants.RDFa.ABOUT);
        elements.attr(Constants.RDFa.RESOURCE, "");

        return new ByteArrayInputStream(doc.toString().getBytes());
    }

    @Test
    void generateAnnotationsSkipsTermOccurrencesWhichAlreadyExistBasedOnSelectors() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple.html");
        generateFile();
        sut.generateAnnotations(content, file);
        assertEquals(1, findAllOccurrencesOf(term).size());
        final InputStream contentReloaded = loadFile("data/rdfa-simple.html");
        sut.generateAnnotations(contentReloaded, file);
        assertEquals(1, findAllOccurrencesOf(term).size());
    }

    @Test
    void generateAnnotationsCreatesTermOccurrenceWhenItHasExistingSelectorButReferencesDifferentTerm()
            throws Exception {
        final Term otherTerm = new Term();
        otherTerm.setUri(Generator.generateUri());
        otherTerm.setLabel(MultilingualString
                .create("Other term", cz.cvut.kbss.termit.environment.Environment.LANGUAGE));
        final TextQuoteSelector selector = new TextQuoteSelector("Územní plán");
        selector.setPrefix("RDFa simple");
        selector.setSuffix(" hlavního města Prahy.");
        final FileOccurrenceTarget t = new FileOccurrenceTarget(file);
        t.setSelectors(Collections.singleton(selector));
        final TermOccurrence to = new TermFileOccurrence(otherTerm.getUri(), t);
        transactional(() -> {
            em.persist(t);
            em.persist(otherTerm);
            em.persist(to);
        });
        final InputStream content = loadFile("data/rdfa-simple.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final List<TermOccurrence> allOccurrences = termOccurrenceDao.findAllTargeting(file);
        assertEquals(2, allOccurrences.size());
        assertTrue(allOccurrences.stream().anyMatch(o -> o.getTerm().equals(otherTerm.getUri())));
        assertTrue(allOccurrences.stream().anyMatch(o -> o.getTerm().equals(term.getUri())));
    }

    @Test
    void generateAnnotationsCreatesTermAssignmentsForOccurrencesWithSufficientScore() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final List<TermAssignment> result = getAssignments();
        assertEquals(1, result.size());
        assertEquals("http://onto.fel.cvut.cz/ontologies/mpp/domains/uzemni-plan",
                result.get(0).getTerm().toString());
    }

    private List<TermAssignment> getAssignments() {
        return em.createNativeQuery("SELECT ?x WHERE { " +
                "?x a ?assignment ." +
                "FILTER NOT EXISTS {" +
                "  ?x a ?occurrence ." +
                "}}", TermAssignment.class)
                 .setParameter("assignment", URI.create(Vocabulary.s_c_prirazeni_termu))
                 .setParameter("occurrence", URI.create(Vocabulary.s_c_vyskyt_termu)).getResultList();
    }

    @Test
    void generateAnnotationsDoesNotCreateAssignmentsForOccurrencesWithInsufficientScore() throws Exception {
        config.getTextAnalysis().setTermAssignmentMinScore(Double.toString(Double.MAX_VALUE));
        final InputStream content = loadFile("data/rdfa-simple.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final List<TermAssignment> result = getAssignments();
        assertTrue(result.isEmpty());
    }

    @Test
    void generateAnnotationsCreatesSingleAssignmentForMultipleOccurrencesOfTerm() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple-multiple-occurrences.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final List<TermAssignment> result = getAssignments();
        assertEquals(1, result.size());
        assertEquals("http://onto.fel.cvut.cz/ontologies/mpp/domains/uzemni-plan",
                result.get(0).getTerm().toString());
    }

    @Test
    void generateAnnotationsCreatesAssignmentsWithTypeSuggestedForOccurrencesWithSufficientScore() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple.html");
        generateFile();
        sut.generateAnnotations(content, file);
        final List<TermAssignment> result = getAssignments();
        assertFalse(result.isEmpty());
        result.forEach(ta -> assertTrue(ta.getTypes().contains(Vocabulary.s_c_navrzene_prirazeni_termu)));
    }

    @Test
    void repeatedAnnotationGenerationDoesNotIncreaseTotalNumberOfTermOccurrencesForResource() throws Exception {
        generateFile();
        sut.generateAnnotations(loadFile("data/rdfa-simple.html"), file);
        final List<TermOccurrence> occurrencesOne = termOccurrenceDao.findAllTargeting(file);
        sut.generateAnnotations(loadFile("data/rdfa-simple.html"), file);
        final List<TermOccurrence> occurrencesTwo = termOccurrenceDao.findAllTargeting(file);
        assertEquals(occurrencesOne.size(), occurrencesTwo.size());
        final int instanceCount = em.createNativeQuery("SELECT (count(*) as ?count) WHERE {" +
                "?x a ?termOccurrence ." +
                "}", Integer.class).setParameter("termOccurrence", URI.create(Vocabulary.s_c_vyskyt_termu))
                                    .getSingleResult();
        assertEquals(occurrencesTwo.size(), instanceCount);
    }

    @Test
    void repeatedAnnotationGenerationDoesNotOverwriteConfirmedAnnotations() throws Exception {
        generateFile();
        sut.generateAnnotations(loadFile("data/rdfa-simple.html"), file);
        final List<TermOccurrence> occurrencesOne = termOccurrenceDao.findAllTargeting(file);
        final List<TermOccurrence> confirmed = occurrencesOne.stream().filter(to -> Generator.randomBoolean()).collect(
                Collectors.toList());
        transactional(() -> confirmed.forEach(to -> {
            to.removeType(Vocabulary.s_c_navrzeny_vyskyt_termu);
            em.merge(to);
        }));
        sut.generateAnnotations(loadFile("data/rdfa-simple.html"), file);
        final List<TermOccurrence> occurrencesTwo = termOccurrenceDao.findAllTargeting(file);
        assertEquals(occurrencesOne.size(), occurrencesTwo.size());
        confirmed.forEach(to -> assertTrue(occurrencesTwo.stream().anyMatch(toA -> toA.getUri().equals(to.getUri()))));
    }

    @Test
    void generateAnnotationsCreatesAnnotationsForOccurrencesInTermDefinition() {
        // This is the term in whose definition were discovered by text analysis is their target
        final Term source = Generator.generateTermWithId();
        sut.generateAnnotations(loadFile("data/rdfa-simple.html"), source);
        final List<TermOccurrence> result = findAllOccurrencesOf(term);
        assertEquals(1, result.size());
        result.forEach(occ -> assertEquals(source.getUri(), occ.getTarget().getSource()));
    }

    @Test
    void generateAnnotationsCreatesAnnotationsWithSuggestedStateForoccurrencesInTermDefinition() {
        // This is the term in whose definition were discovered by text analysis is their target
        final Term source = Generator.generateTermWithId();
        sut.generateAnnotations(loadFile("data/rdfa-simple.html"), source);
        final List<TermOccurrence> result = findAllOccurrencesOf(term);
        result.forEach(occ -> assertThat(occ.getTypes(), hasItem(Vocabulary.s_c_navrzeny_vyskyt_termu)));
    }
}
