package cz.cvut.kbss.termit.service.term;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestAsyncConfig;
import cz.cvut.kbss.termit.environment.config.TestAuthenticationStackConfig;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.assignment.DefinitionalOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionalOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.selector.TextPositionSelector;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.repository.removal.SubTermRemovalStrategy;
import cz.cvut.kbss.termit.service.repository.removal.TermRemovalParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ContextConfiguration(classes = {TestAsyncConfig.class, TestAuthenticationStackConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermOccurrenceCleanupListenerTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private TermService termService;

    @Autowired
    private TermOccurrenceService termOccurrenceService;

    @Autowired
    private TermDao termDao;

    @MockitoSpyBean
    private TermOccurrenceDao termOccurrenceDao;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    private final AtomicReference<Thread> cleanupThread = new AtomicReference<>();

    private Term term;
    private Term referencingTerm;
    private TermRemovalParams removalParams;

    @BeforeEach
    void setUp() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        assertFalse(user.isAdmin());
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
        enableRdfsInference(em);

        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        term = Generator.generateTermWithId(vocabulary.getUri());
        referencingTerm = Generator.generateTermWithId(vocabulary.getUri());
        vocabulary.addRootTerm(term);
        vocabulary.addRootTerm(referencingTerm);
        final AccessControlList acl = new AccessControlList();
        acl.addRecord(new UserAccessControlRecord(AccessLevel.SECURITY, user.toUser()));
        transactional(() -> {
            em.persist(acl, descriptorFactory.accessControlListDescriptor());
            vocabulary.setAcl(acl.getUri());
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(term));
            em.persist(referencingTerm, descriptorFactory.termDescriptor(referencingTerm));
        });

        doAnswer(invocation -> {
                    cleanupThread.set(Thread.currentThread());
                    return invocation.callRealMethod();
                })
                .when(termOccurrenceDao)
                .removeAllOf(any());

        this.removalParams = new TermRemovalParams(term, SubTermRemovalStrategy.FAIL, true, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            awaitCleanup();
        } finally {
            Environment.resetCurrentUser();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removesOccurrencesAfterRemoveWithParamsCommitForAuthorizedNonAdmin(boolean suggested) throws Exception {
        persistOccurrence(suggested);

        transactional(() -> {
            termService.remove(removalParams);
            assertTrue(termOccurrenceDao.existsOf(term, false), "Cleanup must wait for commit");
            verify(termOccurrenceDao, never()).removeAllOf(any());
        });

        assertCleanupCompleted();
    }

    @Test
    void removesSuggestedOccurrencesAfterTermRemoval() throws Exception {
        persistOccurrence(true);

        transactional(() -> {
            termService.remove(term);
            assertTrue(termOccurrenceDao.existsOf(term, false), "Cleanup must wait for commit");
            verify(termOccurrenceDao, never()).removeAllOf(any());
        });

        assertCleanupCompleted();
    }

    @Test
    void doesNotCleanUpOccurrencesWhenRemovalRollsBack() throws Exception {
        persistOccurrence(false);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            termService.remove(removalParams);
            status.setRollbackOnly();
        });

        awaitCleanup();
        assertTrue(termDao.exists(term.getUri()));
        assertTrue(termOccurrenceDao.existsOf(term, false));
        verify(termOccurrenceDao, never()).removeAllOf(any());
    }

    @Test
    void rejectsRemovalAndCleanupWithoutVocabularyPermission() throws Exception {
        persistOccurrence(false);
        final UserAccount unauthorized = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(unauthorized));
        Environment.setCurrentUser(unauthorized);

        assertThrows(AccessDeniedException.class, () -> termService.remove(removalParams));
        assertThrows(AccessDeniedException.class, () -> termOccurrenceService.removeAllOf(term));

        awaitCleanup();
        assertTrue(termDao.exists(term.getUri()));
        assertTrue(termOccurrenceDao.existsOf(term, false));
        verify(termOccurrenceDao, never()).removeAllOf(any());
    }

    private void persistOccurrence(boolean suggested) {
        final TermOccurrence occurrence =
                new TermDefinitionalOccurrence(term.getUri(), new DefinitionalOccurrenceTarget(referencingTerm));
        occurrence.getTarget().setSelectors(Set.of(new TextPositionSelector(0, 10)));
        if (suggested) {
            occurrence.markSuggested();
        }
        transactional(() -> termOccurrenceDao.persist(occurrence));
        assertTrue(termOccurrenceDao.existsOf(term, false));
    }

    private void assertCleanupCompleted() throws Exception {
        awaitCleanup();
        assertFalse(termDao.exists(term.getUri()));
        assertFalse(termOccurrenceDao.existsOf(term, false));

        assertNotNull(cleanupThread.get());
        assertNotSame(Thread.currentThread(), cleanupThread.get(), "Cleanup must execute asynchronously");

        final ArgumentCaptor<AbstractTerm> removedTerm = ArgumentCaptor.forClass(AbstractTerm.class);
        verify(termOccurrenceDao).removeAllOf(removedTerm.capture());
        assertEquals(term.getUri(), removedTerm.getValue().getUri());
        assertEquals(term.getVocabulary(), removedTerm.getValue().getVocabulary());
    }

    private void awaitCleanup() throws Exception {
        // The single worker executes this barrier after all previously submitted cleanup attempts finish.
        taskExecutor.submit(() -> {}).get(10, TimeUnit.SECONDS);
    }
}
