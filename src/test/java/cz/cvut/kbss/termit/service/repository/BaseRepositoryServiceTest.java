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

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("service")
class BaseRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private Validator validator;

    @Autowired
    private BaseRepositoryServiceImpl sut;

    @MockitoBean
    private UserAccountDao userAccountDaoMock;

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

    @Test
    void persistExecutesTransactionalPersist() {
        final UserAccount user = Generator.generateUserAccountWithPassword();

        sut.persist(user);
        verify(userAccountDaoMock).persist(user);
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
    void persistExecutesPostPersistMethodAfterPersistOnDao() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final BaseRepositoryServiceImpl sut = spy(new BaseRepositoryServiceImpl(userAccountDaoMock, validator));

        sut.persist(user);
        final InOrder inOrder = Mockito.inOrder(sut, userAccountDaoMock);
        inOrder.verify(userAccountDaoMock).persist(user);
        inOrder.verify(sut).postPersist(user);
    }

    @Test
    void updateExecutesTransactionalUpdate() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        when(userAccountDaoMock.exists(user.getUri())).thenReturn(true);
        doAnswer(inv -> inv.getArgument(0)).when(userAccountDaoMock).update(user);

        final String updatedLastName = "Married";
        user.setLastName(updatedLastName);
        sut.update(user);

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountDaoMock).update(captor.capture());
        assertEquals(updatedLastName, captor.getValue().getLastName());
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
        when(userAccountDaoMock.exists(user.getUri())).thenReturn(true);

        sut.remove(user);
        verify(userAccountDaoMock).remove(user);
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
        when(userAccountDaoMock.find(instance.getUri())).thenReturn(Optional.of(instance));

        final UserAccount result = sut.findRequired(instance.getUri());
        assertNotNull(result);
        verify(userAccountDaoMock).find(instance.getUri());
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

    @Test
    void getReferenceRetrievesReferenceFromDaoWhenInstanceExists() {
        final UserAccount instance = Generator.generateUserAccountWithPassword();
        when(userAccountDaoMock.exists(instance.getUri())).thenReturn(true);
        when(userAccountDaoMock.getReference(instance.getUri())).thenReturn(instance);

        final UserAccount result = sut.getReference(instance.getUri());
        assertNotNull(result);
        verify(userAccountDaoMock).getReference(instance.getUri());
    }

    @Test
    void getReferenceThrowsNotFoundExceptionWhenInstanceDoesNotExist() {
        final URI id = Generator.generateUri();
        assertThrows(NotFoundException.class, () -> sut.getReference(id));
    }
}
