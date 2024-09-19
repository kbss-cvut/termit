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
package cz.cvut.kbss.termit.service.cache;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.persistence.dao.acl.AccessControlListDao;
import jakarta.annotation.Nonnull;
import org.springframework.cache.interceptor.KeyGenerator;

import java.lang.reflect.Method;

/**
 * Resolves access control list (ACL) cache key for a specified ACL.
 * <p>
 * The cache uses identifiers of subjects of ACLs as keys, so this generator attempts to the identifier of the provided
 * ACL.
 */
public class AccessControlListCacheKeyGenerator implements KeyGenerator {

    private final AccessControlListDao aclDao;

    public AccessControlListCacheKeyGenerator(@Nonnull AccessControlListDao aclDao) {
        this.aclDao = aclDao;
    }

    @Override
    public @Nonnull Object generate(@Nonnull Object target, @Nonnull Method method, Object... params) {
        assert params.length == 1 && params[0] instanceof AccessControlList;
        final AccessControlList acl = (AccessControlList) params[0];
        return aclDao.resolveSubjectOf(acl)
                     .orElseThrow(() -> new NotFoundException("Subject of ACL " + acl + " not found."));
    }
}
