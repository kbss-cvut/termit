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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeRecordServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ChangeRecordService sut;

    @Autowired
    private ChangeRecordDao dao;

    private User author;

    private Vocabulary asset;

    @BeforeEach
    void setUp() {
        this.asset = Generator.generateVocabularyWithId();
        this.author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> {
            em.persist(author);
            em.persist(asset, descriptorFactory.vocabularyDescriptor(asset));
        });
    }

    @Test
    void getChangesReturnsChangesForSpecifiedAsset() {
        enableRdfsInference(em);
        final List<AbstractChangeRecord> records = generateChanges();
        records.sort(Comparator.comparing(AbstractChangeRecord::getTimestamp).reversed());

        final List<AbstractChangeRecord> result = sut.getChanges(asset);
        assertEquals(records, result);
    }

    private List<AbstractChangeRecord> generateChanges() {
        final List<AbstractChangeRecord> records = IntStream.range(0, 5).mapToObj(i -> {
            final UpdateChangeRecord r = new UpdateChangeRecord(asset);
            r.setChangedAttribute(URI.create(DC.Terms.TITLE));
            r.setAuthor(author);
            r.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis() - i * 10000L));
            return r;
        }).collect(Collectors.toList());
        transactional(() -> {
            records.forEach(r -> dao.persist(r, asset));
        });
        return records;
    }
}
