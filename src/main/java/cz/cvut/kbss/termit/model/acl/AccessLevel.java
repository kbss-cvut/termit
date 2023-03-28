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
    NONE(Vocabulary.s_i_zadna),
    /**
     * Read access to an asset. May include exporting, commenting, or snapshot display.
     */
    @Individual(iri = Vocabulary.s_i_cteni)
    READ(Vocabulary.s_i_cteni, NONE),
    /**
     * Write access to an asset. The user can edit the asset.
     */
    @Individual(iri = Vocabulary.s_i_zapis)
    WRITE(Vocabulary.s_i_zapis, NONE, READ),
    /**
     * User can edit or remove an asset and manage access of other users/user groups to it.
     */
    @Individual(iri = Vocabulary.s_i_sprava)
    SECURITY(Vocabulary.s_i_sprava, NONE, READ, WRITE);

    private final String iri;

    private final Set<AccessLevel> included;

    AccessLevel(String iri, AccessLevel... included) {
        this.iri = iri;
        this.included = Set.of(included);
    }

    public boolean includes(AccessLevel requested) {
        return this == requested || included.contains(requested);
    }

    public String getIri() {
        return iri;
    }
}
