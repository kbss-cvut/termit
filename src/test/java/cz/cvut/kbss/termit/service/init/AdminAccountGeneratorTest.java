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
package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountGeneratorTest {

    private static final URI ADMIN_URI = URI.create(Vocabulary.ONTOLOGY_IRI_TERMIT + "/system-admin-user");

    @Spy
    private Configuration config = new Configuration();

    @Mock
    private UserRepositoryService userService;

    private String adminCredentialsDir;

    private AdminAccountGenerator sut;

    @BeforeEach
    void setUp() {
        // Randomize admin credentials folder
        this.adminCredentialsDir =
                System.getProperty("java.io.tmpdir") + File.separator + Generator.randomInt(0, 10000);
        config.getAdmin().setCredentialsLocation(adminCredentialsDir);
        config.getAdmin().setCredentialsFile(".termit-admin");
        this.sut = new AdminAccountGenerator(userService, config);
    }

    @AfterEach
    void tearDown() throws IOException {
        final File toDelete = new File(adminCredentialsDir);
        if (toDelete.exists()) {
            Files.walk(toDelete.toPath())
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }

    @Test
    void persistsSystemAdminWhenHeDoesNotExist() {
        sut.initSystemAdmin();
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userService).persist(captor.capture());
        assertEquals(ADMIN_URI, captor.getValue().getUri());
    }

    @Test
    void doesNotCreateNewAdminWhenOneAlreadyExists() {
        when(userService.doesAdminExist()).thenReturn(true);
        sut.initSystemAdmin();
        verify(userService, never()).persist(any(UserAccount.class));
    }

    @Test
    void doesGenerateAdminWhenOIDCSecurityIsUsed() {
        config.getSecurity().setProvider(Configuration.Security.ProviderType.OIDC);
        sut.initSystemAdmin();
        verify(userService, never()).persist(any(UserAccount.class));
    }

    @Test
    void savesAdminLoginCredentialsIntoHiddenFileInUserHome() throws Exception {
        doAnswer(arg -> {
            final UserAccount account = arg.getArgument(0, UserAccount.class);
            account.setPassword(new BCryptPasswordEncoder().encode(account.getPassword()));
            return null;
        }).when(userService).persist(any(UserAccount.class));
        sut.initSystemAdmin();
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userService).persist(captor.capture());
        final String home = config.getAdmin().getCredentialsLocation();
        final File credentialsFile = new File(home + File.separator + config.getAdmin().getCredentialsFile());
        assertTrue(credentialsFile.exists());
        assertTrue(credentialsFile.isHidden());
        verifyAdminCredentialsFileContent(captor.getValue(), credentialsFile);
    }

    private void verifyAdminCredentialsFileContent(UserAccount admin, File credentialsFile) throws IOException {
        final List<String> lines = Files.readAllLines(credentialsFile.toPath());
        assertThat(lines.get(0), containsString(admin.getUsername() + "/"));
        final String password = lines.get(0).substring(lines.get(0).indexOf('/') + 1);
        assertTrue(new BCryptPasswordEncoder().matches(password, admin.getPassword()));
    }

    @Test
    void savesAdminLoginCredentialsIntoConfiguredFile() throws Exception {
        doAnswer(arg -> {
            final UserAccount account = arg.getArgument(0, UserAccount.class);
            account.setPassword(new BCryptPasswordEncoder().encode(account.getPassword()));
            return null;
        }).when(userService).persist(any(UserAccount.class));
        final String adminFileName = ".admin-file-with-different-name";
        config.getAdmin().setCredentialsFile(adminFileName);
        this.sut = new AdminAccountGenerator(userService, config);
        sut.initSystemAdmin();
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userService).persist(captor.capture());
        final File credentialsFile = new File(adminCredentialsDir + File.separator + adminFileName);
        assertTrue(credentialsFile.exists());
        assertTrue(credentialsFile.isHidden());
        verifyAdminCredentialsFileContent(captor.getValue(), credentialsFile);
    }

    @Test
    void ensuresGeneratedAccountIsAdmin() {
        sut.initSystemAdmin();
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userService).persist(captor.capture());
        assertThat(captor.getValue().getTypes(), hasItem(Vocabulary.s_c_administrator_termitu));
        assertThat(captor.getValue().getTypes(), not(hasItem(Vocabulary.s_c_omezeny_uzivatel_termitu)));
    }
}
