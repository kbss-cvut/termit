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
package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private VocabularyContextMapper vocabularyContextMapper;

    @Autowired
    private Configuration config;

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void validateUsesOverrideRulesToAllowI18n() {
        final Vocabulary vocabulary = generateVocabulary();
        transactional(() -> {
            final Validator sut = new Validator(em, vocabularyContextMapper, config);
            final List<ValidationResult> result = sut.validate(Collections.singleton(vocabulary.getUri()));
            assertTrue(result.stream().noneMatch(
                    vr -> vr.getMessage().get("en").contains("The term does not have a preferred label in Czech")));
            assertTrue(result.stream().noneMatch(
                    vr -> vr.getMessage().get("en").contains("The term does not have a definition in Czech")));
            assertTrue(result.stream().anyMatch(vr -> vr.getMessage().get("en").contains(
                    "The term does not have a preferred label in the primary configured language of this deployment of TermIt")));
        });
    }

    private Vocabulary generateVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.getLabel().remove(Constants.DEFAULT_LANGUAGE);
        term.getLabel().set("de", "Apfelbaum, der");
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
        });
        return vocabulary;
    }
}
