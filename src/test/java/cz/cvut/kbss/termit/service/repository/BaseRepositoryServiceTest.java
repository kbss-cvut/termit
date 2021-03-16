/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.validation.Validator;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("service")
class BaseRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private UserAccountDao userAccountDao;

    @Autowired
    private Validator validator;

    @Autowired
    private BaseRepositoryServiceImpl sut;

    @TestConfiguration
    public static class Config {

        @Bean
        public BaseRepositoryServiceImpl baseRepositoryService(UserAccountDao userAccountDao, Validator validator) {
            return new BaseRepositoryServiceImpl(userAccountDao, validator);
        }

        @Bean
        public LocalValidatorFactoryBean validatorFactoryBean() {
            return new LocalValidatorFactoryBean();
        }
    }

    @Mock
    private UserAccountDao userAccountDaoMock;

    @Test
    void persistExecutesTransactionalPersist() {
        final UserAccount user = Generator.generateUserAccountWithPassword();

        sut.persist(user);
        assertTrue(userAccountDao.exists(user.getUri()));
    }

    @Test
    void persistExecutesPrePersistMethodBeforePersistOnDao() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        sut.persist(user);
        final InOrder inOrder = Mockito.inOrder(sut, userAccountDaoMock);
        inOrder.verify(sut).prePersist(user);
        inOrder.verify(userAccountDaoMock).persist(user);
    }

    @Test
    void updateExecutesTransactionalUpdate() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> userAccountDao.persist(user));

        final String updatedLastName = "Married";
        user.setLastName(updatedLastName);
        sut.update(user);

        final Optional<UserAccount> result = userAccountDao.find(user.getUri());
        assertAll(() -> assertTrue(result.isPresent()),
                () -> assertEquals(updatedLastName, result.get().getLastName())
        );
    }

    @Test
    void updateExecutesPreUpdateMethodBeforeUpdateOnDao() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        when(userAccountDaoMock.exists(user.getUri())).thenReturn(true);
        when(userAccountDaoMock.update(any())).thenReturn(user);
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        sut.update(user);
        final InOrder inOrder = Mockito.inOrder(sut, userAccountDaoMock);
        inOrder.verify(sut).preUpdate(user);
        inOrder.verify(userAccountDaoMock).update(user);
    }

    @Test
    void updateInvokesPostUpdateAfterUpdateOnDao() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final UserAccount returned = Generator.generateUserAccountWithPassword();
        when(userAccountDaoMock.exists(user.getUri())).thenReturn(true);
        when(userAccountDaoMock.update(any())).thenReturn(returned);
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        final UserAccount result = sut.update(user);
        final InOrder inOrder = Mockito.inOrder(sut, userAccountDaoMock);
        inOrder.verify(userAccountDaoMock).update(user);
        inOrder.verify(sut).postUpdate(returned);
        assertEquals(returned, result);
    }

    @Test
    void removeExecutesTransactionalRemove() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> userAccountDao.persist(user));

        sut.remove(user);
        assertFalse(userAccountDao.exists(user.getUri()));
    }

    @Test
    void removeByIdExecutesTransactionalRemove() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> userAccountDao.persist(user));

        sut.remove(user);
        assertFalse(userAccountDao.exists(user.getUri()));
    }

    @Test
    void findExecutesPostLoadAfterLoadingEntityFromDao() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        when(userAccountDaoMock.find(user.getUri())).thenReturn(Optional.of(user));
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        final Optional<UserAccount> result = sut.find(user.getUri());
        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        final InOrder inOrder = Mockito.inOrder(sut, userAccountDaoMock);
        inOrder.verify(userAccountDaoMock).find(user.getUri());
        inOrder.verify(sut).postLoad(user);
    }

    @Test
    void findDoesNotExecutePostLoadWhenNoEntityIsFoundByDao() {
        when(userAccountDaoMock.find(any())).thenReturn(Optional.empty());
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        final Optional<UserAccount> result = sut.find(Generator.generateUri());
        assertFalse(result.isPresent());
        verify(sut, never()).postLoad(any());
    }

    @Test
    void findAllExecutesPostLoadForEachLoadedEntity() {
        final List<UserAccount> users = IntStream.range(0, 5).mapToObj(i -> Generator.generateUserAccountWithPassword())
                .collect(Collectors.toList());
        when(userAccountDaoMock.findAll()).thenReturn(users);
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        final List<UserAccount> result = sut.findAll();
        assertEquals(users, result);
        final InOrder inOrder = Mockito.inOrder(sut, userAccountDaoMock);
        inOrder.verify(userAccountDaoMock).findAll();
        users.forEach(u -> inOrder.verify(sut).postLoad(u));
    }

    @Test
    void existsInvokesDao() {
        final URI id = Generator.generateUri();
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));
        assertFalse(sut.exists(id));
        verify(userAccountDaoMock).exists(id);
    }

    @Test
    void removeInvokesPreAndPostHooks() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        final InOrder inOrder = inOrder(sut, userAccountDaoMock);
        sut.remove(user);
        inOrder.verify(sut).preRemove(user);
        inOrder.verify(userAccountDaoMock).remove(user);
        inOrder.verify(sut).postRemove(user);
    }

    @Test
    void updateThrowsNotFoundExceptionWhenInstanceDoesNotExistInRepository() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        assertThrows(NotFoundException.class, () -> sut.update(user));
    }

    @Test
    void findRequiredRetrievesObjectById() {
        final UserAccount instance = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(instance));

        final UserAccount result = sut.findRequired(instance.getUri());
        assertEquals(instance, result);
    }

    @Test
    void findRequiredThrowsNotFoundExceptionWhenMatchingInstanceIsNotFound() {
        assertThrows(NotFoundException.class, () -> sut.findRequired(Generator.generateUri()));
    }

    @Test
    void findRequiredInvokesPostLoadOnLoadedInstance() {
        final UserAccount instance = Generator.generateUserAccountWithPassword();
        when(userAccountDaoMock.find(instance.getUri())).thenReturn(Optional.of(instance));
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        final UserAccount result = sut.findRequired(instance.getUri());
        assertEquals(instance, result);
        final InOrder inOrder = Mockito.inOrder(sut, userAccountDaoMock);
        inOrder.verify(userAccountDaoMock).find(instance.getUri());
        inOrder.verify(sut).postLoad(instance);
    }
}
