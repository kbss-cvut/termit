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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CachingVocabularyContextMapperTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    private CachingVocabularyContextMapper sut;

    @BeforeEach
    void setUp() {
        this.sut = new CachingVocabularyContextMapper(em);
    }

    @Test
    void getVocabularyContextResolvesVocabularyContext() {
        Map<Vocabulary, URI> map = generateVocabularies();
        sut.load();
        map.forEach((v, ctx) -> assertEquals(ctx, sut.getVocabularyContext(v)));
    }

    private Map<Vocabulary, URI> generateVocabularies() {
        final Map<Vocabulary, URI> map = new HashMap<>();
        transactional(() -> IntStream.range(0, Generator.randomInt(2, 5)).forEach(i -> {
            final Vocabulary v = Generator.generateVocabularyWithId();
            final URI context = Generator.randomBoolean() ? Generator.generateUri() : v.getUri();
            final Descriptor descriptor = new EntityDescriptor(context);
            em.persist(v, descriptor);
            map.put(v, context);
        }));
        return map;
    }

    @Test
    void getVocabularyContextReturnsVocabularyIriWhenNoContextForSpecifiedVocabularyExists() {
        sut.load();
        final URI vocabularyUri = Generator.generateUri();
        assertEquals(vocabularyUri, sut.getVocabularyContext(vocabularyUri));
    }

    @Test
    void getVocabularyContextThrowsAmbiguousVocabularyContextExceptionWhenMultipleContextsForVocabularyAreDetermined() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, new EntityDescriptor(v.getUri())));
        transactional(() -> em.persist(v, new EntityDescriptor(Generator.generateUri())));
        sut.load();

        assertThrows(AmbiguousVocabularyContextException.class, () -> sut.getVocabularyContext(v));
    }

    @Test
    void reloadsCachedContextsOnVocabularyCreatedEvent() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, new EntityDescriptor(v.getUri())));
        sut.load();
        final Vocabulary newVocabulary = Generator.generateVocabularyWithId();
        final URI context = Generator.generateUri();
        transactional(() -> em.persist(newVocabulary, new EntityDescriptor(context)));

        // No mapping -> return vocabulary IRI
        assertEquals(newVocabulary.getUri(), sut.getVocabularyContext(newVocabulary));
        sut.load(); // Event handler
        assertEquals(context, sut.getVocabularyContext(newVocabulary));
    }

    @Test
    void getVocabularyContextReturnsCanonicalContextWhenAnotherInstanceIsBasedOnIt() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, new EntityDescriptor(v.getUri())));
        final URI workingVersionCtx = Generator.generateUri();
        transactional(() -> {
            em.persist(v, new EntityDescriptor(workingVersionCtx));
            DefaultVocabularyContextMapperTest.generateCanonicalContextReference(workingVersionCtx, v.getUri(), em);
        });
        sut.load();

        assertEquals(v.getUri(), sut.getVocabularyContext(v.getUri()));
    }
}
