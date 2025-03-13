package cz.cvut.kbss.termit.dto.statistics;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.util.Vocabulary;

/**
 * Type of asset for which statistics of number of items are available.
 */
public enum CountableAssetType {
    TERM(SKOS.CONCEPT),
    VOCABULARY(Vocabulary.s_c_slovnik),
    USER(Vocabulary.s_c_uzivatel_termitu);

    private final String typeUri;

    CountableAssetType(String typeUri) {
        this.typeUri = typeUri;
    }

    public String getTypeUri() {
        return typeUri;
    }
}
