package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.persistence.dao.PersonalAccessTokenDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
public class PersonalAccessTokenRepositoryServiceTest extends BaseServiceTestRunner {
    @Mock
    private PersonalAccessTokenDao tokenDao;

    @Autowired
    private Validator validator;

    @Autowired
    private DtoMapper dtoMapper;

    private PersonalAccessTokenRepositoryService sut;

    @BeforeEach
    void setUp() {
        sut = new PersonalAccessTokenRepositoryService(validator, tokenDao, dtoMapper);
    }

    @Test
    public void persistGeneratesId() {
        PersonalAccessToken token = new PersonalAccessToken();
        token.setOwner(Generator.generateUserAccountWithPassword());

        doNothing().when(tokenDao).persist(notNull(PersonalAccessToken.class));
        sut.persist(token);

        assertNotNull(token.getUri());
    }
}
