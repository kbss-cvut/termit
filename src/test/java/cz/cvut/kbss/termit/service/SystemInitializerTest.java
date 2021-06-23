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
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SystemInitializerTest extends BaseServiceTestRunner {

    private static final URI ADMIN_URI = URI.create(Vocabulary.ONTOLOGY_IRI_termit + "/system-admin-user");

    @Autowired
    private Configuration config;

    @Autowired
    private UserRepositoryService userService;

    @Autowired
    private EntityManager em;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminCredentialsDir;

    private SystemInitializer sut;

    @BeforeEach
    void setUp() {
        // Randomize admin credentials folder
        this.adminCredentialsDir =
                System.getProperty("java.io.tmpdir") + File.separator + Generator.randomInt(0, 10000);
        config.getAdmin().setCredentialsLocation(adminCredentialsDir);
        this.sut = new SystemInitializer(config, userService, txManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        final File dir = new File(adminCredentialsDir);
        if (dir.listFiles() != null) {
            for (File child : dir.listFiles()) {
                Files.deleteIfExists(child.toPath());
            }
        }
        Files.deleteIfExists(dir.toPath());
    }

    @Test
    void persistsSystemAdminWhenHeDoesNotExist() {
        sut.initSystemAdmin();
        assertNotNull(em.find(UserAccount.class, ADMIN_URI));
    }

    @Test
    void doesNotCreateNewAdminWhenOneAlreadyExists() {
        sut.initSystemAdmin();
        final UserAccount admin = em.find(UserAccount.class, ADMIN_URI);
        sut.initSystemAdmin();
        final UserAccount result = em.find(UserAccount.class, ADMIN_URI);
        // We know that password is generated, so the same password means no new instance was created
        assertEquals(admin.getPassword(), result.getPassword());
    }

    @Test
    void doesNotCreateNewAdminWhenDifferentAdminAlreadyExists() {
        final UserAccount differentAdmin = Generator.generateUserAccount();
        differentAdmin.addType(Vocabulary.s_c_administrator_termitu);
        transactional(() -> em.persist(differentAdmin));
        sut.initSystemAdmin();
        assertNull(em.find(UserAccount.class, ADMIN_URI));
    }

    @Test
    void savesAdminLoginCredentialsIntoHiddenFileInUserHome() throws Exception {
        sut.initSystemAdmin();
        final UserAccount admin = em.find(UserAccount.class, ADMIN_URI);
        final String home = config.getAdmin().getCredentialsLocation();
        final File credentialsFile = new File(home + File.separator + config.getAdmin().getCredentialsFile());
        assertTrue(credentialsFile.exists());
        assertTrue(credentialsFile.isHidden());
        verifyAdminCredentialsFileContent(admin, credentialsFile);
    }

    private void verifyAdminCredentialsFileContent(UserAccount admin, File credentialsFile) throws IOException {
        final List<String> lines = Files.readAllLines(credentialsFile.toPath());
        assertThat(lines.get(0), containsString(admin.getUsername() + "/"));
        final String password = lines.get(0).substring(lines.get(0).indexOf('/') + 1);
        assertTrue(passwordEncoder.matches(password, admin.getPassword()));
    }

    @Test
    void savesAdminLoginCredentialsIntoConfiguredFile() throws Exception {
        final String adminFileName = ".admin-file-with-different-name";
        config.getAdmin().setCredentialsFile(adminFileName);
        this.sut = new SystemInitializer(config, userService, txManager);
        sut.initSystemAdmin();
        final UserAccount admin = em.find(UserAccount.class, ADMIN_URI);
        final File credentialsFile = new File(adminCredentialsDir + File.separator + adminFileName);
        assertTrue(credentialsFile.exists());
        assertTrue(credentialsFile.isHidden());
        verifyAdminCredentialsFileContent(admin, credentialsFile);
    }

    @Test
    void ensuresGeneratedAccountIsAdmin() {
        sut.initSystemAdmin();
        final UserAccount result = em.find(UserAccount.class, ADMIN_URI);
        assertNotNull(result);
        assertThat(result.getTypes(), hasItem(Vocabulary.s_c_administrator_termitu));
        assertThat(result.getTypes(), not(hasItem(Vocabulary.s_c_omezeny_uzivatel_termitu)));
    }
}
