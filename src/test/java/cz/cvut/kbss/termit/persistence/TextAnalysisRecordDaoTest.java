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
package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.persistence.dao.TextAnalysisRecordDao;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAnalysisRecordDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TextAnalysisRecordDao sut;

    private Resource resource;

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        this.resource = Generator.generateResourceWithId();
        transactional(() -> {
            em.persist(author);
            em.persist(resource);
        });
    }

    @Test
    void findLatestGetsLatestTextAnalysisRecordForResource() {
        final URI vocabulary = Generator.generateUri();
        final TextAnalysisRecord old = new TextAnalysisRecord(Instant.ofEpochMilli(System.currentTimeMillis() - 10000), resource);
        old.setVocabularies(Collections.singleton(vocabulary));
        final TextAnalysisRecord latest = new TextAnalysisRecord(Utils.timestamp(), resource);
        latest.setVocabularies(Collections.singleton(vocabulary));
        transactional(() -> {
            sut.persist(old);
            sut.persist(latest);
        });

        final Optional<TextAnalysisRecord> result = sut.findLatest(resource);
        assertTrue(result.isPresent());
        assertEquals(latest, result.get());
    }

    @Test
    void findLatestReturnsEmptyOptionalForResourceWithoutTextAnalysisRecords() {
        final Optional<TextAnalysisRecord> result = sut.findLatest(resource);
        assertFalse(result.isPresent());
    }
}
