package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.Individual;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Set;

/**
 * Levels of access to an asset.
 * <p>
 * The access levels are hierarchical, i.e., higher levels of access include lower (more restricted) levels.
 */
public enum AccessLevel {
    /**
     * The most restricted access level. The asset is not even visible to the user.
     */
    @Individual(iri = Vocabulary.s_i_zadna)
    NONE(),
    /**
     * Read access to an asset. May include exporting, commenting, or snapshot display.
     */
    @Individual(iri = Vocabulary.s_i_cteni)
    READ(NONE),
    /**
     * Write access to an asset. The user can edit the asset.
     */
    @Individual(iri = Vocabulary.s_i_zapis)
    WRITE(NONE, READ),
    /**
     * User can edit or remove an asset and manage access of other users/user groups to it.
     */
    @Individual(iri = Vocabulary.s_i_sprava)
    SECURITY(NONE, READ, WRITE);

    private final Set<AccessLevel> included;

    AccessLevel(AccessLevel... included) {
        this.included = Set.of(included);
    }

    public boolean includes(AccessLevel requested) {
        return this == requested || included.contains(requested);
    }
}
