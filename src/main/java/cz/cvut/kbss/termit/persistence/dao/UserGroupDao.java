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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class UserGroupDao extends BaseDao<UserGroup> {

    private final DescriptorFactory descriptorFactory;

    public UserGroupDao(EntityManager em, DescriptorFactory descriptorFactory) {
        super(UserGroup.class, em);
        this.descriptorFactory = descriptorFactory;
    }

    @Override
    public void persist(UserGroup entity) {
        Objects.requireNonNull(entity);
        if (entity.getUri() == null) {
            entity.setUri(IdentifierResolver.generateSyntheticIdentifier(Vocabulary.s_c_sioc_Usergroup));
        }
        super.persist(entity);
    }

    @Override
    protected Descriptor getDescriptor() {
        return descriptorFactory.userGroupDescriptor();
    }
}
