package cz.cvut.kbss.termit.service.init.lucene;

import java.util.Set;

/**
 * Provides list of currently indexed languages for Full Text Search
 */
public interface IndexedLanguagesProvider {
    Set<String> getIndexedLanguages();
}
