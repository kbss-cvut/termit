package cz.cvut.kbss.termit.model.acl;

import java.util.Set;

/**
 * Levels of access to an asset.
 * <p>
 * The access levels are hierarchical, i.e., higher levels of access include lower (more restricted) levels.
 *
 * TODO Map to individuals from owl:oneOf
 */
public enum AccessLevel {
    /**
     * The most restricted access level. The asset is not even visible to the user.
     */
    NONE(),
    /**
     * Read access to an asset. May include exporting, commenting, or snapshot display.
     */
    READ(NONE),
    /**
     * Write access to an asset. The user can edit the asset.
     */
    WRITE(NONE, READ),
    /**
     * User can edit or remove an asset and manage access of other users/user groups to it.
     */
    SECURITY(NONE, READ, WRITE);

    private final Set<AccessLevel> included;

    AccessLevel(AccessLevel... included) {
        this.included = Set.of(included);
    }

    public boolean includes(AccessLevel requested) {
        return this == requested || included.contains(requested);
    }
}
