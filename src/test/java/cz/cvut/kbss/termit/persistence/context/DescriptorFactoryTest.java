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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.loaders.PersistenceUnitClassFinder;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.MetamodelImpl;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.FieldSpecification;
import cz.cvut.kbss.jopa.utils.Configuration;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.MainPersistenceFactory;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DescriptorFactoryTest {

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    private Term term;

    @Mock
    private EntityManagerFactory emf;

    @Mock
    private VocabularyDao vocabularyDao;

    @Mock
    private VocabularyContextMapper vocabularyContextMapper;

    @InjectMocks
    private DescriptorFactory sut;

    @BeforeEach
    void setUp() {
        this.term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        final Configuration jopaConfig = new Configuration(MainPersistenceFactory.defaultParams());
        final MetamodelImpl metamodel = new MetamodelImpl(jopaConfig);
        metamodel.build(new PersistenceUnitClassFinder());
        when(emf.getMetamodel()).thenReturn(metamodel);
    }

    @Test
    void termDescriptorSetsRelatedVocabulariesContextsForSkosMappingProperties() {
        final Set<URI> relatedVocabularies = Set.of(Generator.generateUri(), Generator.generateUri(), vocabulary.getUri());
        final Map<URI, URI> vocToCtx = new HashMap<>();
        relatedVocabularies.forEach(u -> {
            final URI ctx = Generator.generateUri();
            when(vocabularyContextMapper.getVocabularyContext(u)).thenReturn(ctx);
            vocToCtx.put(u, ctx);
        });
        when(vocabularyDao.getRelatedVocabularies(any(Vocabulary.class), anyCollection())).thenReturn(relatedVocabularies);

        final Descriptor result = sut.termDescriptor(term);
        final EntityType<Term> et = emf.getMetamodel().entity(Term.class);
        final Set<URI> contexts = new HashSet<>(vocToCtx.values());
        assertEquals(contexts, result.getAttributeDescriptor(et.getFieldSpecification("externalParentTerms")).getContexts());
        assertEquals(contexts, result.getAttributeDescriptor(et.getFieldSpecification("exactMatchTerms")).getContexts());
        assertEquals(contexts, result.getAttributeDescriptor(et.getFieldSpecification("relatedMatch")).getContexts());
        verify(vocabularyDao).getRelatedVocabularies(vocabulary, Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS);
    }

    @Test
    void termDescriptorUsesRootDescriptorForSkosConceptProperties() {
        final URI ctx = Generator.generateUri();
        when(vocabularyDao.getRelatedVocabularies(any(Vocabulary.class), anyCollection())).thenReturn(
                Collections.singleton(vocabulary.getUri()));
        when(vocabularyContextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(ctx);

        final Descriptor result = sut.termDescriptor(term);
        final EntityType<Term> et = emf.getMetamodel().entity(Term.class);
        assertEquals(result, result.getAttributeDescriptor(et.getFieldSpecification("parentTerms")));
        assertEquals(result, result.getAttributeDescriptor(et.getFieldSpecification("related")));
    }

    @Test
    void termDescriptorForSaveSetsDefaultContextForSkosMappingProperties() {
        final URI ctx = Generator.generateUri();
        when(vocabularyContextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(ctx);

        final Descriptor result = sut.termDescriptorForSave(term);
        final EntityType<Term> et = emf.getMetamodel().entity(Term.class);
        assertThat(result.getAttributeDescriptor(et.getFieldSpecification("externalParentTerms")).getContexts(), emptyCollectionOf(URI.class));
        assertThat(result.getAttributeDescriptor(et.getFieldSpecification("exactMatchTerms")).getContexts(), emptyCollectionOf(URI.class));
        assertThat(result.getAttributeDescriptor(et.getFieldSpecification("relatedMatch")).getContexts(), emptyCollectionOf(URI.class));
        assertEquals(result, result.getAttributeDescriptor(et.getFieldSpecification("parentTerms")));
        assertEquals(result, result.getAttributeDescriptor(et.getFieldSpecification("related")));
    }

    @Test
    void fileDescriptorContainsAlsoDescriptorForDocument() {
        final File file = Generator.generateFileWithId("test.html");
        final Document doc = Generator.generateDocumentWithId();
        doc.addFile(file);
        file.setDocument(doc);
        doc.setVocabulary(Generator.generateUri());
        final Descriptor result = sut.fileDescriptor(doc.getVocabulary());
        final FieldSpecification<?, ?> docFieldSpec = emf.getMetamodel().entity(File.class).getFieldSpecification("document");
        final Descriptor docDescriptor = result.getAttributeDescriptor(docFieldSpec);
        assertNotNull(docDescriptor);
    }
}
