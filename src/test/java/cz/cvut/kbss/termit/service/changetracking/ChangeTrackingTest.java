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
package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.DeleteChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static cz.cvut.kbss.termit.environment.Environment.setPrimaryLabel;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChangeTrackingTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ChangeRecordDao changeRecordDao;

    @Autowired
    private VocabularyRepositoryService vocabularyService;

    @Autowired
    private TermRepositoryService termService;

    @Autowired
    private ResourceRepositoryService resourceService;

    private User author;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        this.vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(author));
    }

    @Test
    void persistingVocabularyCreatesCreationChangeRecord() {
        enableRdfsInference(em);
        transactional(() -> vocabularyService.persist(vocabulary));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(vocabulary);
        assertEquals(1, result.size());
        assertEquals(vocabulary.getUri(), result.get(0).getChangedEntity());
        assertThat(result.get(0), instanceOf(PersistChangeRecord.class));
    }

    @Test
    void persistingTermCreatesCreationChangeRecord() {
        enableRdfsInference(em);
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final Term term = Generator.generateTermWithId();
        transactional(() -> termService.addRootTermToVocabulary(term, vocabulary));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(term);
        assertEquals(1, result.size());
        assertEquals(term.getUri(), result.get(0).getChangedEntity());
        assertThat(result.get(0), instanceOf(PersistChangeRecord.class));
    }

    @Test
    void updatingVocabularyLiteralAttributeCreatesUpdateChangeRecord() {
        enableRdfsInference(em);
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        setPrimaryLabel(vocabulary, "Updated vocabulary label");
        transactional(() -> vocabularyService.update(vocabulary));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(vocabulary);
        assertEquals(1, result.size());
        assertEquals(vocabulary.getUri(), result.get(0).getChangedEntity());
        assertThat(result.get(0), instanceOf(UpdateChangeRecord.class));
        assertEquals(DC.Terms.TITLE, ((UpdateChangeRecord) result.get(0)).getChangedAttribute().toString());
    }

    @Test
    void updatingVocabularyReferenceAndLiteralAttributesCreatesTwoUpdateRecords() {
        enableRdfsInference(em);
        final Vocabulary imported = Generator.generateVocabularyWithId();
        transactional(() -> {
            em.persist(imported, descriptorFactory.vocabularyDescriptor(imported));
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });
        setPrimaryLabel(vocabulary, "Updated vocabulary label");
        vocabulary.setImportedVocabularies(Collections.singleton(imported.getUri()));
        transactional(() -> vocabularyService.update(vocabulary));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(vocabulary);
        assertEquals(2, result.size());
        result.forEach(chr -> {
            assertEquals(vocabulary.getUri(), chr.getChangedEntity());
            assertThat(result.get(0), instanceOf(UpdateChangeRecord.class));
            assertThat(((UpdateChangeRecord) chr).getChangedAttribute().toString(), anyOf(equalTo(DC.Terms.TITLE),
                    equalTo(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik)));
        });
    }

    @Test
    void updatingTermLiteralAttributeCreatesChangeRecord() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        term.setDefinition(MultilingualString.create("Updated term definition.", Environment.LANGUAGE));
        // This is normally inferred
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> termService.update(term));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(term);
        assertEquals(1, result.size());
        assertEquals(term.getUri(), result.get(0).getChangedEntity());
        assertThat(result.get(0), instanceOf(UpdateChangeRecord.class));
        assertEquals(SKOS.DEFINITION, ((UpdateChangeRecord) result.get(0)).getChangedAttribute().toString());
    }

    @Test
    void updatingTermReferenceAttributeCreatesChangeRecord() {
        enableRdfsInference(em);
        final Term parent = Generator.generateTermWithId();
        final Term term = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            parent.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        term.addParentTerm(parent);
        // This is normally inferred
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> termService.update(term));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(term);
        assertEquals(1, result.size());
        assertEquals(term.getUri(), result.get(0).getChangedEntity());
        assertThat(result.get(0), instanceOf(UpdateChangeRecord.class));
        assertEquals(SKOS.BROADER, ((UpdateChangeRecord) result.get(0)).getChangedAttribute().toString());
    }

    @Test
    void updatingTermLiteralAttributesCreatesChangeRecordWithOriginalAndNewValue() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId();
        final MultilingualString originalDefinition = term.getDefinition();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        final MultilingualString newDefinition = MultilingualString
                .create("Updated term definition.", Environment.LANGUAGE);
        term.setDefinition(newDefinition);
        // This is normally inferred
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> termService.update(term));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(term);
        assertEquals(1, result.size());
        assertEquals(Collections.singleton(originalDefinition),
                ((UpdateChangeRecord) result.get(0)).getOriginalValue());
        assertEquals(Collections.singleton(newDefinition), ((UpdateChangeRecord) result.get(0)).getNewValue());
    }

    @Test
    void updatingTermReferenceAttributeCreatesChangeRecordWithOriginalAndNewValue() {
        enableRdfsInference(em);
        final Term parent = Generator.generateTermWithId();
        final Term term = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            parent.setGlossary(vocabulary.getGlossary().getUri());
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(parent, descriptorFactory.termDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(parent, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        term.addParentTerm(parent);
        // This is normally inferred
        term.setVocabulary(vocabulary.getUri());
        transactional(() -> termService.update(term));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(term);
        assertFalse(result.isEmpty());
        assertThat(((UpdateChangeRecord) result.get(0)).getOriginalValue(), anyOf(nullValue(), empty()));
        assertEquals(Collections.singleton(parent.getUri()), ((UpdateChangeRecord) result.get(0)).getNewValue());
    }

    @Test
    void persistingFileDoesNotCreatePersistChangeRecord() {
        enableRdfsInference(em);
        final File file = Generator.generateFileWithId("test.html");
        transactional(() -> resourceService.persist(file));

        final List<AbstractChangeRecord> result = changeRecordDao.findAll(file);
        assertEquals(0, result.size());
    }

    @Test
    void updatingTermStateCreatesUpdateChangeRecord() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        termService.setState(term, Generator.randomItem(Generator.TERM_STATES));
        final List<AbstractChangeRecord> result = changeRecordDao.findAll(term);
        assertEquals(1, result.size());
        assertThat(result.get(0), instanceOf(UpdateChangeRecord.class));
        assertEquals(URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_stav_pojmu),
                ((UpdateChangeRecord) result.get(0)).getChangedAttribute());
    }

    @Test
    void deletingTermCreatesDeleteChangeRecord() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(()-> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        termService.remove(term);
        final List<AbstractChangeRecord> result = changeRecordDao.findAll(term);
        assertEquals(1, result.size());
        final DeleteChangeRecord record = assertInstanceOf(DeleteChangeRecord.class, result.get(0));
        assertEquals(term.getUri(), record.getChangedEntity());
        assertNotNull(record.getLabel());
        assertEquals(term.getLabel(), record.getLabel());
    }
}
