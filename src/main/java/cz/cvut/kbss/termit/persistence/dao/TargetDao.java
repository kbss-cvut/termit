/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.assignment.Target;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

@Repository
public class TargetDao extends BaseDao<Target> {

    @Autowired
    public TargetDao(EntityManager em) {
        super(Target.class, em);
    }

    /**
     * Finds a target for the resource as a whole (i.e. not targets for selectors are returned)
     */
    public Optional<Target> findByWholeResource(final Resource resource) {
        Objects.requireNonNull(resource);
        try {
            return Optional.ofNullable(em.createNativeQuery(
                    "SELECT ?x WHERE {" + "?x a ?type ." + "?x ?hasSource ?resource . "
                            + "FILTER NOT EXISTS {?x ?hasSelector ?selector} }", Target.class)
                                         .setParameter("type", typeUri).setParameter("hasSource",
                            URI.create(Vocabulary.s_p_ma_zdroj)).setParameter("hasSelector",
                            URI.create(Vocabulary.s_p_ma_selektor))
                                         .setParameter("resource", resource.getUri())
                                         .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
