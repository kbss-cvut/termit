package cz.cvut.kbss.termit.util;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.util.Assert;

public class VocabularyTest {

    @Test
    // @todo until https://github.com/kbss-cvut/jopa/issues/85 is resolved
    public void ensureContentHasCorrectUrl() {
        Assert.equals("http://rdfs.org/sioc/ns#content", Vocabulary.s_p_content_A);
    }
}
