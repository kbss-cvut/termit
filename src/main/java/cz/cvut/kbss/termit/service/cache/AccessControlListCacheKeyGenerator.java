package cz.cvut.kbss.termit.service.cache;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.persistence.dao.acl.AccessControlListDao;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.lang.NonNull;

import java.lang.reflect.Method;

/**
 * Resolves access control list (ACL) cache key for a specified ACL.
 * <p>
 * The cache uses identifiers of subjects of ACLs as keys, so this generator attempts to the identifier of the provided
 * ACL.
 */
public class AccessControlListCacheKeyGenerator implements KeyGenerator {

    private final AccessControlListDao aclDao;

    public AccessControlListCacheKeyGenerator(@NonNull AccessControlListDao aclDao) {
        this.aclDao = aclDao;
    }

    @Override
    public @NotNull Object generate(@NotNull Object target, @NotNull Method method, Object @NotNull ... params) {
        assert params.length == 1 && params[0] instanceof AccessControlList;
        final AccessControlList acl = (AccessControlList) params[0];
        return aclDao.resolveSubjectOf(acl)
                     .orElseThrow(() -> new NotFoundException("Subject of ACL " + acl + " not found."));
    }
}
