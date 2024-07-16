package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.PasswordChangeRequestDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.PasswordChangeRequestDao;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordChangeRequestRepositoryService extends BaseRepositoryService<PasswordChangeRequest, PasswordChangeRequestDto> {

    private final PasswordChangeRequestDao passwordChangeRequestDao;
    private final DtoMapper dtoMapper;

    protected PasswordChangeRequestRepositoryService(PasswordChangeRequestDao passwordChangeRequestDao, DtoMapper dtoMapper, Validator validator) {
        super(validator);
        this.passwordChangeRequestDao = passwordChangeRequestDao;
        this.dtoMapper = dtoMapper;
    }

    @Override
    protected GenericDao<PasswordChangeRequest> getPrimaryDao() {
        return passwordChangeRequestDao;
    }

    public PasswordChangeRequest create(UserAccount userAccount) {
        PasswordChangeRequest token = new PasswordChangeRequest();
        token.setUserAccount(userAccount);
        token.setToken(UUID.randomUUID().toString());
        token.setCreatedAt(Instant.now());

        passwordChangeRequestDao.persist(token);
        return token;
    }

    public PasswordChangeRequest findRequired(String username) {
        return find(username).orElseThrow(() -> NotFoundException.create(PasswordChangeRequest.class.getSimpleName(), username));
    }

    public Optional<PasswordChangeRequest> find(String username) {
        return passwordChangeRequestDao.find(username);
    }

    @Override
    protected PasswordChangeRequestDto mapToDto(PasswordChangeRequest entity) {
        return dtoMapper.passwordChangeRequestToDto(entity);
    }
}
