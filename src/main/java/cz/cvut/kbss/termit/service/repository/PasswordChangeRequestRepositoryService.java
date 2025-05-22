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

import cz.cvut.kbss.termit.dto.PasswordChangeRequestDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.PasswordChangeRequestDao;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PasswordChangeRequestRepositoryService
        extends BaseRepositoryService<PasswordChangeRequest, PasswordChangeRequestDto> {

    private final PasswordChangeRequestDao passwordChangeRequestDao;
    private final DtoMapper dtoMapper;

    public PasswordChangeRequestRepositoryService(PasswordChangeRequestDao passwordChangeRequestDao,
                                                  DtoMapper dtoMapper, Validator validator) {
        super(validator);
        this.passwordChangeRequestDao = passwordChangeRequestDao;
        this.dtoMapper = dtoMapper;
    }

    @Override
    protected GenericDao<PasswordChangeRequest> getPrimaryDao() {
        return passwordChangeRequestDao;
    }

    public PasswordChangeRequest create(UserAccount userAccount) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setUserAccount(userAccount);
        request.setToken(UUID.randomUUID().toString());
        request.setCreatedAt(Utils.timestamp());

        passwordChangeRequestDao.persist(request);
        postPersist(request);
        return request;
    }

    public List<PasswordChangeRequest> findAllByUserAccount(UserAccount userAccount) {
        List<PasswordChangeRequest> loaded = passwordChangeRequestDao.findAllByUserAccount(userAccount);
        return loaded.stream().map(this::postLoad).toList();
    }

    @Override
    protected PasswordChangeRequestDto mapToDto(PasswordChangeRequest entity) {
        return dtoMapper.passwordChangeRequestToDto(entity);
    }
}
