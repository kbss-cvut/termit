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
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

abstract class VocabularyExporterTestBase extends BaseServiceTestRunner {

    @Autowired
    EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    Vocabulary vocabulary;

    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        final User author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> {
            em.persist(author);
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(Generator.generatePersistChange(vocabulary));
        });
    }

    List<Term> generateTerms() {
        return generateTerms(vocabulary);
    }

    List<Term> generateTerms(Vocabulary target) {
        final List<Term> terms = new ArrayList<>(10);
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final Term term = Generator.generateTermWithId();
            if (Generator.randomBoolean()) {
                term.setSources(Collections.singleton("PSP/c-1/p-2/b-c"));
            }
            terms.add(term);
            term.setGlossary(target.getGlossary().getUri());
        }
        target.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            em.merge(target.getGlossary(), descriptorFactory.glossaryDescriptor(target));
            terms.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(target)));
            terms.forEach(t -> Generator.addTermInVocabularyRelationship(t, target.getUri(), em));
        });
        return terms;
    }
}
