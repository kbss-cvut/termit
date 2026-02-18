package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.persistence.dao.PersonalAccessTokenDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
public class PersonalAccessTokenRepositoryServiceTest {
    @Mock
    private PersonalAccessTokenDao tokenDao;

    private PersonalAccessTokenRepositoryService sut;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        DtoMapper dtoMapper = Mappers.getMapper(DtoMapper.class);
        sut = new PersonalAccessTokenRepositoryService(validator, tokenDao, dtoMapper);
    }

    @Test
    public void persistGeneratesId() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(Generator.generateUserAccount());

        doNothing().when(tokenDao).persist(notNull(PersonalAccessToken.class));
        sut.persist(token);

        assertNotNull(token.getUri());
    }
}
