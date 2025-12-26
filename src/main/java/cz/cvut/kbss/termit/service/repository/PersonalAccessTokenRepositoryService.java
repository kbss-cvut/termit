package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.PersonalAccessTokenDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.PersonalAccessToken_;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.PersonalAccessTokenDao;
import jakarta.annotation.Nonnull;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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
    protected PersonalAccessTokenDto mapToDto(PersonalAccessToken entity) {
        return dtoMapper.personalAccessTokenToDto(entity);
    }

    private URI generateId() {
        final URI newId = URI.create(PersonalAccessToken_.entityClassIRI.toString() + "/" + UUID.randomUUID());
        if (exists(newId)) {
            throw ResourceExistsException.create(PersonalAccessToken.class.getSimpleName(), newId);
        }
        return newId;
    }

    @Override
    protected void prePersist(@Nonnull PersonalAccessToken instance) {
        instance.setUri(generateId());
        super.prePersist(instance);
    }

    @Transactional(readOnly = true)
    public List<PersonalAccessTokenDto> findAllByUserAccount(UserAccount userAccount) {
        return dao.findAllByUserAccount(userAccount).stream().map(this::mapToDto).toList();
    }
}
