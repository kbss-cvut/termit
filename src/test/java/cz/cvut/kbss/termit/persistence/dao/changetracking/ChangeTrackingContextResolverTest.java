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
package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeTrackingContextResolverTest {

    private static final String CHANGE_CONTEXT_EXTENSION = "/changes";

    @Mock
    private EntityManager em;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration config;

    private ChangeTrackingContextResolver sut;

    @BeforeEach
    void setUp() {
        when(config.getChangetracking().getContext().getExtension()).thenReturn(CHANGE_CONTEXT_EXTENSION);
        this.sut = new ChangeTrackingContextResolver(em, config);
    }

    @Test
    void resolveChangeTrackingContextReturnsVocabularyIdentifierWithTrackingExtensionForVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final URI result = sut.resolveChangeTrackingContext(vocabulary);
        assertNotNull(result);
        assertEquals(vocabulary.getUri().toString().concat(CHANGE_CONTEXT_EXTENSION), result.toString());
    }

    @Test
    void resolveChangeTrackingContextReturnsVocabularyIdentifierWithTrackingExtensionForTerm() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.getGlossary().setUri(Generator.generateUri());
        final Term term = Generator.generateTermWithId();
        term.setGlossary(vocabulary.getGlossary().getUri());
        final TypedQuery<URI> q = mock(TypedQuery.class);
        when(q.setParameter(any(String.class), any(Object.class))).thenReturn(q);
        when(q.getSingleResult()).thenReturn(vocabulary.getUri());
        when(em.createNativeQuery(anyString(), eq(URI.class))).thenReturn(q);
        final URI result = sut.resolveChangeTrackingContext(term);
        assertNotNull(result);
        assertEquals(vocabulary.getUri().toString().concat(CHANGE_CONTEXT_EXTENSION), result.toString());
    }

    @Test
    void resolveChangeTrackingContextReturnsResourceIdentifierWithTrackingExtensionForResource() {
        final Resource resource = Generator.generateResourceWithId();
        final URI result = sut.resolveChangeTrackingContext(resource);
        assertNotNull(result);
        assertEquals(resource.getUri().toString().concat(CHANGE_CONTEXT_EXTENSION), result.toString());
    }
}
