package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.PersonalAccessTokenDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.PersonalAccessTokenDao;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PersonalAccessTokenRepositoryService extends BaseRepositoryService<PersonalAccessToken, PersonalAccessTokenDto> {

    private final PersonalAccessTokenDao dao;
    private final DtoMapper dtoMapper;

    protected PersonalAccessTokenRepositoryService(Validator validator, PersonalAccessTokenDao dao, DtoMapper dtoMapper) {
        super(validator);
        this.dao = dao;
        this.dtoMapper = dtoMapper;
    }

    @Override
    protected GenericDao<PersonalAccessToken> getPrimaryDao() {
        return dao;
    }

    @Override
    public PersonalAccessTokenDto mapToDto(PersonalAccessToken entity) {
        return dtoMapper.personalAccessTokenToDto(entity);
    }

    @Transactional(readOnly = true)
    public List<PersonalAccessTokenDto> findAllByUserAccount(UserAccount userAccount) {
        return dao.findAllByUserAccount(userAccount).stream().map(this::mapToDto).toList();
    }
}
