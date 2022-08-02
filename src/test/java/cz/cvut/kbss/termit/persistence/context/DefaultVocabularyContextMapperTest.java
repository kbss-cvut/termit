package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultVocabularyContextMapperTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    private DefaultVocabularyContextMapper sut;

    @BeforeEach
    void setUp() {
        this.sut = new DefaultVocabularyContextMapper(em);
    }

    @Test
    void getVocabularyContextResolvesVocabularyContextFromRepository() {
        final URI context = Generator.generateUri();
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, new EntityDescriptor(context)));

        assertEquals(context, sut.getVocabularyContext(vocabulary));
    }

    @Test
    void getVocabularyContextReturnsVocabularyUriWhenNoContextIsFoundInRepository() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        assertEquals(vocabulary.getUri(), sut.getVocabularyContext(vocabulary.getUri()));
    }

    @Test
    void getVocabularyContextThrowsAmbiguousVocabularyContextExceptionWhenMultipleContextsForVocabularyAreDetermined() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, new EntityDescriptor(v.getUri())));
        transactional(() -> em.persist(v, new EntityDescriptor(Generator.generateUri())));

        assertThrows(AmbiguousVocabularyContextException.class, () -> sut.getVocabularyContext(v));
    }

    @Test
    void getVocabularyContextReturnsCanonicalContextWhenAnotherInstanceIsBasedOnIt() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, new EntityDescriptor(v.getUri())));
        v.setProperties(Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_vychazi_z_verze,
                                                 Collections.singleton(v.getUri().toString())));
        final URI workingVersionCtx = Generator.generateUri();
        transactional(() -> em.persist(v, new EntityDescriptor(workingVersionCtx)));

        assertEquals(v.getUri(), sut.getVocabularyContext(v.getUri()));
    }
}
