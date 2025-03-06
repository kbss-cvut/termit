package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.RoleAccessControlRecord;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import cz.cvut.kbss.termit.service.repository.UserRoleRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

import static cz.cvut.kbss.termit.service.repository.RepositoryAccessControlListService.isAnonymous;
import static cz.cvut.kbss.termit.service.repository.RepositoryAccessControlListService.isRestricted;

@Service
public class VocabularyAnonymousAccessControlListGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(VocabularyAnonymousAccessControlListGenerator.class);

    private final EntityManager em;

    private final VocabularyRepositoryService vocabularyService;

    private final AccessControlListService aclService;
    private final UserRoleRepositoryService userRoleRepositoryService;


    public VocabularyAnonymousAccessControlListGenerator(EntityManager em,
                                                         VocabularyRepositoryService vocabularyService,
                                                         AccessControlListService aclService,
                                                         UserRoleRepositoryService userRoleRepositoryService) {
        this.em = em;
        this.vocabularyService = vocabularyService;
        this.aclService = aclService;
        this.userRoleRepositoryService = userRoleRepositoryService;
    }

    /**
     * Creates {@link RoleAccessControlRecord}
     * with {@link cz.cvut.kbss.termit.security.model.UserRole#ANONYMOUS_USER ANONYMOUS_USER}
     * for {@link cz.cvut.kbss.termit.model.Vocabulary Vocabulary}s
     * that are missing such record.
     * <p>
     * Access level is assigned based on an existing record for {@link cz.cvut.kbss.termit.security.model.UserRole#RESTRICTED_USER RESTRICTED_USER}.
     * {@link AccessLevel#NONE AccessLevel#NONE} is assigned when the restricted user has no access,
     * otherwise {@link AccessLevel#READ AccessLevel#READ} is assigned for the anonymous user.
     * <p>
     * This method is asynchronous to prevent slowing down the system startup.
     */
    @Async
    @Transactional
    public void generateMissingAccessControlLists() {
        LOG.debug("Generating missing vocabulary access control records for anonymous users.");
        final UserRole anonymousRole = userRoleRepositoryService.findRequired(cz.cvut.kbss.termit.security.model.UserRole.ANONYMOUS_USER);
        final List<URI> vocabsWithAcl = resolveVocabulariesWithAcl();
        // remove vocabularies that already have anonymous user access record
        vocabsWithAcl.removeAll(resolveVocabulariesWithAnonymousRecord());

        LOG.trace("Generating anonymous access control records for vocabularies: {}.", vocabsWithAcl);
        vocabsWithAcl.forEach(vUri -> {
            final cz.cvut.kbss.termit.model.Vocabulary v = vocabularyService.findRequired(vUri);
            aclService.findFor(v).ifPresentOrElse(acl -> {
                if (hasAnonymous(acl)) return; // skip if already has anonymous access
                LOG.debug("Generating missing anonymous access control record for vocabulary {}.", v);
                final AccessLevel accessLevel = shouldAllowAnonymousAccess(acl) ? AccessLevel.READ : AccessLevel.NONE;
                aclService.addRecord(acl, new RoleAccessControlRecord(accessLevel, anonymousRole));
            }, () -> LOG.warn("Vocabulary {} is missing an ACL.", v));
        });
        LOG.trace("Finished generating vocabulary access control records for anonymous users.");
    }

    /**
     * @return list of vocabulary IRIs which have access control list
     */
    private List<URI> resolveVocabulariesWithAcl() {
        return em.createNativeQuery("""
                         SELECT DISTINCT ?v WHERE {
                            ?v a ?vocabulary ;
                            ?hasAcl ?acl .
                         }
                         """, URI.class)
                 .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                 .setParameter("hasAcl", URI.create(Vocabulary.s_p_ma_seznam_rizeni_pristupu))
                 .getResultList();
    }

    /**
     * @return list of vocabulary IRIs that already have access control record for anonymous user
     */
    private List<URI> resolveVocabulariesWithAnonymousRecord() {
        return em.createNativeQuery("""
                         SELECT DISTINCT ?v WHERE {
                             ?v a ?vocabulary ;
                             ?hasAcl ?acl .
                             ?acl ?hasRecord ?record .
                             ?record a ?userRoleRecord ;
                             ?hasHolder ?anonymousUser .
                         }
                         """, URI.class)
                 .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                 .setParameter("hasAcl", URI.create(Vocabulary.s_p_ma_seznam_rizeni_pristupu))
                 .setParameter("hasRecord", URI.create(Vocabulary.s_p_ma_zaznam_rizeni_pristupu))
                 .setParameter("userRoleRecord", URI.create(Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_role))
                 .setParameter("hasHolder", URI.create(Vocabulary.s_p_ma_drzitele_pristupovych_opravneni))
                 .setParameter("anonymousUser", URI.create(Vocabulary.s_c_anonymni_uzivatel_termitu))
                 .getResultList();
    }

    /**
     * @return true when the ACL contains record for anonymous user role
     */
    private boolean hasAnonymous(AccessControlList acl) {
        return acl.getRecords().stream().anyMatch(r -> r.getHolder() instanceof UserRole role && isAnonymous(role));
    }

    /**
     * @return true when the ACL contains READ or greater access level for the restricted user
     */
    private boolean shouldAllowAnonymousAccess(AccessControlList acl) {
        return acl.getRecords().stream().anyMatch(r ->
                r.getHolder() instanceof UserRole role && isRestricted(role) && r.getAccessLevel()
                                                                                 .includes(AccessLevel.READ)
        );
    }
}
