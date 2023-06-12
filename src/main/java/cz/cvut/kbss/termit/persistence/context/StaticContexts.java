package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.util.Vocabulary;

/**
 * Declares static repository contexts.
 * <p>
 * That is, repository contexts that are not dependent on individual instances but are rather derived, for example, from
 * an entity class.
 */
public class StaticContexts {

    /**
     * Repository context for storing {@link cz.cvut.kbss.termit.model.UserGroup} instances.
     */
    public static final String USER_GROUPS = Vocabulary.s_c_Usergroup;

    /**
     * Repository context for storing {@link cz.cvut.kbss.termit.model.acl.AccessControlList} instances.
     */
    public static final String ACCESS_CONTROL_LISTS = Vocabulary.s_c_seznam_rizeni_pristupu;

    private StaticContexts() {
        throw new AssertionError();
    }
}
