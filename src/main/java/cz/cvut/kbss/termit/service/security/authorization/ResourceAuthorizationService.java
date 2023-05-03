package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

/**
 * Authorizes access to resources, files and documents in particular.
 */
@Service
public class ResourceAuthorizationService implements AssetAuthorizationService<Resource> {

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    public ResourceAuthorizationService(VocabularyAuthorizationService vocabularyAuthorizationService) {
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
    }

    @Override
    public boolean canRead(Resource asset) {
        return true;
    }

    @Override
    public boolean canModify(Resource asset) {
        return resolveVocabulary(asset).map(vocabularyAuthorizationService::canModify).orElse(true);
    }

    private Optional<Vocabulary> resolveVocabulary(Resource resource) {
        if (resource instanceof Document) {
            final URI vocIri = ((Document) resource).getVocabulary();
            return vocIri != null ? Optional.of(new Vocabulary(vocIri)) : Optional.empty();
        } else if (resource instanceof File) {
            final File f = (File) resource;
            return f.getDocument() != null ? getDocumentVocabulary(f.getDocument()) : Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Vocabulary> getDocumentVocabulary(Document doc) {
        final URI vocIri = doc.getVocabulary();
        return vocIri != null ? Optional.of(new Vocabulary(vocIri)) : Optional.empty();
    }

    @Override
    public boolean canRemove(Resource asset) {
        return resolveVocabulary(asset).map(vocabularyAuthorizationService::canRemoveFiles).orElse(true);
    }
}
