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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseRepositoryServiceImpl extends BaseRepositoryService<UserAccount, UserAccount> {

    private final UserAccountDao userAccountDao;

    @Autowired
    public BaseRepositoryServiceImpl(UserAccountDao userAccountDao, Validator validator) {
        super(validator);
        this.userAccountDao = userAccountDao;
    }

    @Override
    protected GenericDao<UserAccount> getPrimaryDao() {
        return userAccountDao;
    }

    @Override
    protected UserAccount mapToDto(UserAccount entity) {
        return entity;
    }
}
