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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;

@Repository
public class TermAssignmentDao extends BaseDao<TermAssignment> {

    @Autowired
    public TermAssignmentDao(EntityManager em) {
        super(TermAssignment.class, em);
    }

    public List<URI> getUnusedTermsInVocabulary(cz.cvut.kbss.termit.model.Vocabulary vocabulary) {
        return em.createNativeQuery("SELECT DISTINCT ?term WHERE { "
                                 + " ?term ?inVocabulary ?vocabulary . "
                                 + " FILTER NOT EXISTS {?x ?hasTerm ?term ; "
                                 + " ?hasTarget/?hasSource ?resource.}"
                                 + "}",
                         URI.class)
                 .setParameter("vocabulary", vocabulary.getUri())
                 .setParameter("inVocabulary",
                         URI.create(
                                 cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj)).getResultList();
    }
}
