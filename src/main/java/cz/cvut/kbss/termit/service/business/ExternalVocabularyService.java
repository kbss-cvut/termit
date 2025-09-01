package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.model.Vocabulary;
import java.util.List;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * Business logic concerning external vocabularies.
 * 
 */
public interface ExternalVocabularyService extends ApplicationEventPublisherAware{
    
    /**
     * Fetches a list of vocabularies available for import.
     *
     * @return list of available vocabulary information or empty list when connection
     * failed
     */
    public List<RdfsResource> getAvailableVocabularies();
    
    /**
     * Imports multiple vocabularies from external source.
     *
     * @param vocabularyIris List of iris of vocabularies that shall be
     * imported.
     * @return first imported Vocabulary
     */
    public Vocabulary importFromExternalUris(List<String> vocabularyIris);
    
    /**
     * Reload already imported external vocabularies.
     */
    public void reloadVocabularies();

}
