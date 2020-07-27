package cz.cvut.kbss.termit.rest.readonly;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.rest.BaseController;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.readonly.ReadOnlyVocabularyService;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collection;
import java.util.List;

@RestController
@PreAuthorize("permitAll()")
@RequestMapping("/public/vocabularies")
public class ReadOnlyVocabularyController extends BaseController {

    private final ReadOnlyVocabularyService vocabularyService;

    public ReadOnlyVocabularyController(IdentifierResolver idResolver, Configuration config,
                                        ReadOnlyVocabularyService vocabularyService) {
        super(idResolver, config);
        this.vocabularyService = vocabularyService;
    }

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<ReadOnlyVocabulary> getAll() {
        return vocabularyService.findAll();
    }

    @GetMapping(value = "/{fragment}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ReadOnlyVocabulary getById(@PathVariable String fragment,
                                      @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) String namespace) {
        return vocabularyService.findRequired(resolveIdentifier(namespace, fragment, ConfigParam.NAMESPACE_VOCABULARY));
    }

    /**
     * Gets imports (including transitive) of vocabulary with the specified identification
     */
    @GetMapping(value = "/{fragment}/imports", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getTransitiveImports(@PathVariable String fragment,
                                                @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) String namespace) {
        final ReadOnlyVocabulary vocabulary = getById(fragment, namespace);
        return vocabularyService.getTransitivelyImportedVocabularies(vocabulary);
    }
}
