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
package cz.cvut.kbss.termit.persistence.relationship;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkosVocabularyRelationshipResolverTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private SkosVocabularyRelationshipResolver sut;

    @Test
    void getRelatedVocabulariesResolvesDirectlyRelatedVocabulariesViaSkosMappingProperties() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        final Term source = Generator.generateTermWithId();
        final Term target = Generator.generateTermWithId();
        subjectVocabulary.getGlossary().addRootTerm(source);
        targetVocabulary.getGlossary().addRootTerm(target);
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
            source.setGlossary(subjectVocabulary.getGlossary().getUri());
            em.persist(source, descriptorFactory.termDescriptor(subjectVocabulary));
            target.setGlossary(targetVocabulary.getGlossary().getUri());
            em.persist(target, descriptorFactory.termDescriptor(targetVocabulary));
            Generator.addTermInVocabularyRelationship(source, subjectVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(target, targetVocabulary.getUri(), em);
        });
        source.setRelatedMatch(Set.of(new TermInfo(target)));
        transactional(() -> em.merge(source, descriptorFactory.termDescriptor(subjectVocabulary)));

        final Set<URI> result = sut.getRelatedVocabularies(subjectVocabulary.getUri());
        assertEquals(Set.of(targetVocabulary.getUri()), result);
    }
}
