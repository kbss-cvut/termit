package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TermAssignmentDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermAssignmentDao sut;

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> {
            em.persist(author);
        });
    }

    private Document getDocument(final File... files) {
        final Document document = Generator.generateDocumentWithId();
        document.setLabel("Doc");
        Arrays.stream(files).forEach(document::addFile);
        return document;
    }

    @Test
    void loadUnusedTermsInVocabularyWorks() {
        cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term1 = Generator.generateTermWithId();
        term1.setVocabulary(vocabulary.getUri());
        final Term term2 = Generator.generateTermWithId();
        term2.setVocabulary(term1.getVocabulary());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument(file);
        generateTermOccurrences(term2, file);

        transactional(() -> {
            enableRdfsInference(em);
            em.persist(vocabulary);
            em.persist(file);
            em.persist(document);
            em.persist(term1);
            em.persist(term2);
        });

        final List<URI> result = sut.getUnusedTermsInVocabulary(vocabulary);
        assertEquals(1, result.size());
        assertEquals(term1.getUri(), result.get(0));
    }

    private void generateTermOccurrences(Term term, Asset<?> target) {
        final List<TermOccurrence> occurrences = IntStream.range(0, Generator.randomInt(5, 10))
                                                          .mapToObj(i -> Generator.generateTermOccurrence(term, target, false))
                                                          .collect(Collectors.toList());
        transactional(() -> occurrences.forEach(to -> {
            em.persist(to);
            em.persist(to.getTarget());
        }));
    }
}
