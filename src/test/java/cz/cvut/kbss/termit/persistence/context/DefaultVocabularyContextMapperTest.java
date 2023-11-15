/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
        final URI workingVersionCtx = Generator.generateUri();
        transactional(() -> {
            em.persist(v, new EntityDescriptor(workingVersionCtx));
            generateCanonicalContextReference(workingVersionCtx, v.getUri(), em);
        });

        assertEquals(v.getUri(), sut.getVocabularyContext(v.getUri()));
    }

    static void generateCanonicalContextReference(URI context, URI canonical, EntityManager em) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection con = repo.getConnection()) {
            final ValueFactory vf = con.getValueFactory();
            con.add(vf.createIRI(context.toString()), vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_vychazi_z_verze), vf.createIRI(canonical.toString()), vf.createIRI(context.toString()));
        }
    }

    @Test
    void getVocabularyInContextReturnsIdentifierOfVocabularyStoredInSpecifiedContext() {
        final URI context = Generator.generateUri();
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, new EntityDescriptor(context)));

        final Optional<URI> result = sut.getVocabularyInContext(context);
        assertTrue(result.isPresent());
        assertEquals(vocabulary.getUri(), result.get());
    }

    @Test
    void getVocabularyContextReturnsEmptyOptionalWhenSpecifiedContextDoesNotExistOrDoesNotContainVocabulary() {
        final Optional<URI> result = sut.getVocabularyInContext(Generator.generateUri());
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void getVocabularyContextThrowsAmbiguousVocabularyContextExceptionWhenContextContainsMultipleVocabularies() {
        final URI context = Generator.generateUri();
        final Vocabulary vOne = Generator.generateVocabularyWithId();
        final Vocabulary vTwo = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vOne, new EntityDescriptor(context)));
        transactional(() -> em.persist(vTwo, new EntityDescriptor(context)));

        assertThrows(AmbiguousVocabularyContextException.class, () -> sut.getVocabularyInContext(context));
    }
}
