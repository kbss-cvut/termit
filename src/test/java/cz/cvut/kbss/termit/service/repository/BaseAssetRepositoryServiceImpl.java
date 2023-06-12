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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.dao.BaseAssetDao;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Validator;

public class BaseAssetRepositoryServiceImpl extends BaseAssetRepositoryService<Term, Term> {

    private final TermDao dao;

    @Autowired
    public BaseAssetRepositoryServiceImpl(TermDao dao, Validator validator, SecurityUtils securityUtils) {
        super(validator);
        this.dao = dao;
    }

    @Override
    protected BaseAssetDao<Term> getPrimaryDao() {
        return dao;
    }

    @Override
    protected Term mapToDto(Term entity) {
        return entity;
    }
}
