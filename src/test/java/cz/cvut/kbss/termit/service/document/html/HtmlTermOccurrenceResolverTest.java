/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.document.html;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.PropertyMockingApplicationContextInitializer;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(initializers = PropertyMockingApplicationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HtmlTermOccurrenceResolverTest extends BaseServiceTestRunner {

    @Autowired
    private Environment environment;

    @Autowired
    private EntityManager em;

    @Autowired
    private HtmlTermOccurrenceResolver sut;

    @BeforeEach
    void setUp() {
        final User user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        cz.cvut.kbss.termit.environment.Environment.setCurrentUser(user);
    }

    @Test
    void supportsReturnsTrueForFileWithHtmlLabelExtension() {
        final File file = new File();
        file.setLabel("rdfa-simple.html");
        assertTrue(sut.supports(file));
    }

    @Test
    void supportsReturnsTrueForFileWithHtmLabelExtension() {
        final File file = new File();
        file.setLabel("rdfa-simple.htm");
        assertTrue(sut.supports(file));
    }

    // This does not work on JDK 11 (but works on JDK 8 for some reason)
    @Disabled
    @Test
    void supportsReturnsTrueForHtmlFileWithoutExtension() throws Exception {
        final File file = generateFile();
        assertTrue(sut.supports(file));
    }

    private File generateFile() throws IOException {
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        ((MockEnvironment) environment).setProperty("termit.file.storage", dir.getAbsolutePath());
        final Document document = new Document();
        document.setLabel("testDocument");
        document.setUri(Generator.generateUri());
        final java.io.File docDir = new java.io.File(dir.getAbsolutePath() + java.io.File.separator +
                document.getDirectoryName());
        docDir.mkdir();
        docDir.deleteOnExit();
        final java.io.File content = Files.createTempFile(docDir.toPath(), "test", "").toFile();
        content.deleteOnExit();
        Files.copy(getClass().getClassLoader().getResourceAsStream("data/rdfa-simple.html"), content.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        final File file = new File();
        file.setLabel(content.getName());
        file.setDocument(document);
        document.addFile(file);
        return file;
    }

    @Test
    void findTermOccurrencesExtractsAlsoScoreFromRdfa() {
        createTerm();
        final File file = new File();
        file.setLabel("rdfa-simple.html");
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        final List<TermOccurrence> result = sut.findTermOccurrences();
        result.forEach(to -> {
            assertNotNull(to.getScore());
            assertThat(to.getScore(), greaterThan(0.0));
        });
    }

    private void createTerm() {
        final Term term = new Term();
        term.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/mpp/domains/uzemni-plan"));
        term.setLabel(MultilingualString.create("Test term", cz.cvut.kbss.termit.environment.Environment.LANGUAGE));
        transactional(() -> em.persist(term));
    }

    @Test
    void findTermOccurrencesHandlesRdfaWithoutScore() {
        createTerm();
        final File file = new File();
        file.setLabel("rdfa-simple.html");
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple-no-score.html");
        sut.parseContent(is, file);
        final List<TermOccurrence> result = sut.findTermOccurrences();
        result.forEach(to -> assertNull(to.getScore()));
    }

    @Test
    void findTermOccurrencesHandlesInvalidScoreInRdfa() {
        createTerm();
        final File file = new File();
        file.setLabel("rdfa-simple.html");
        final InputStream is = cz.cvut.kbss.termit.environment.Environment
                .loadFile("data/rdfa-simple-invalid-score.html");
        sut.parseContent(is, file);
        final List<TermOccurrence> result = sut.findTermOccurrences();
        result.forEach(to -> assertNull(to.getScore()));
    }

    @Test
    void supportsReturnsTrueForTerm() {
        assertTrue(sut.supports(Generator.generateTermWithId()));
    }
}
