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
