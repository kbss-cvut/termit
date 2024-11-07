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

import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.metamodel.FieldSpecification;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DescriptorFactoryTest extends BaseDaoTestRunner {

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    private Term term;

    private FieldSpecification<?, ?> parentFieldSpec;

    @Autowired
    private DescriptorFactory sut;

    @BeforeEach
    void setUp() throws Exception {
        this.term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        this.parentFieldSpec = mock(FieldSpecification.class);
        when(parentFieldSpec.getJavaField()).thenReturn(Term.class.getDeclaredField("externalParentTerms"));
    }

    @Test
    void termDescriptorCreatesSimpleTermDescriptorWhenNoParentsAreProvided() {
        final Descriptor result = sut.termDescriptor(term);
        assertEquals(Collections.singleton(vocabulary.getUri()), result.getContexts());
        assertEquals(Collections.singleton(vocabulary.getUri()), result.getAttributeContexts(parentFieldSpec));
    }

    @Test
    void termDescriptorCreatesSimpleTermDescriptorWhenParentsAreInSameVocabulary() {
        final Term parent = Generator.generateTermWithId();
        parent.setVocabulary(vocabulary.getUri());
        term.addParentTerm(parent);
        final Descriptor result = sut.termDescriptor(term);
        assertEquals(Collections.singleton(vocabulary.getUri()), result.getContexts());
        assertEquals(Collections.singleton(vocabulary.getUri()), result.getAttributeContexts(parentFieldSpec));
    }

    @Test
    void termDescriptorCreatesDescriptorWithParentTermContextSetToDefaultToAllowParentsFromMultipleVocabularies() {
        final Term parent = Generator.generateTermWithId();
        final URI parentVocabulary = Generator.generateUri();
        parent.setVocabulary(parentVocabulary);
        term.addParentTerm(parent);
        final Descriptor result = sut.termDescriptor(term);
        assertEquals(Collections.singleton(vocabulary.getUri()), result.getContexts());
        assertFalse(result.getAttributeDescriptor(parentFieldSpec).getSingleContext().isPresent());
    }

    @Test
    void termDescriptorCreatesDescriptorWithExactMatchesContextSetToDefaultToAllowExactMatchesFromMultipleVocabularies() {
        final TermInfo exactMatch = Generator.generateTermInfoWithId();
        final URI parentVocabulary = Generator.generateUri();
        exactMatch.setVocabulary(parentVocabulary);
        term.addExactMatch(exactMatch);
        final Descriptor result = sut.termDescriptor(term);
        assertEquals(Collections.singleton(vocabulary.getUri()), result.getContexts());
        assertFalse(result.getAttributeDescriptor(parentFieldSpec).getSingleContext().isPresent());
    }

    @Test
    void fileDescriptorContainsAlsoDescriptorForDocument() throws Exception {
        final File file = Generator.generateFileWithId("test.html");
        final Document doc = Generator.generateDocumentWithId();
        doc.addFile(file);
        file.setDocument(doc);
        doc.setVocabulary(Generator.generateUri());
        final Descriptor result = sut.fileDescriptor(doc.getVocabulary());
        final FieldSpecification<?, ?> docFieldSpec = mock(FieldSpecification.class);
        when(docFieldSpec.getJavaField()).thenReturn(File.class.getDeclaredField("document"));
        final Descriptor docDescriptor = result.getAttributeDescriptor(docFieldSpec);
        assertNotNull(docDescriptor);
    }
}
