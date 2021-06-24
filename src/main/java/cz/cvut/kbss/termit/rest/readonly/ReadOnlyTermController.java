package cz.cvut.kbss.termit.rest.readonly;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.rest.BaseController;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.readonly.ReadOnlyTermService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.security.SecurityConstants.PUBLIC_API_PATH;

@RestController
@PreAuthorize("permitAll()")
@RequestMapping(PUBLIC_API_PATH)
public class ReadOnlyTermController extends BaseController {

    private final ReadOnlyTermService termService;

    public ReadOnlyTermController(IdentifierResolver idResolver, Configuration config,
                                  ReadOnlyTermService termService) {
        super(idResolver, config);
        this.termService = termService;
    }

    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<?> getTerms(@PathVariable String vocabularyIdFragment,
                                       @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace,
                                       @RequestParam(name = "searchString", required = false) String searchString,
                                       @RequestParam(name = "includeImported", required = false) boolean includeImported) {
        final Vocabulary vocabulary = getVocabulary(vocabularyIdFragment, namespace);
        if (searchString != null) {
            return includeImported ? termService.findAllIncludingImported(searchString, vocabulary) :
                   termService.findAll(searchString, vocabulary);
        }
        return termService.findAll(vocabulary);
    }

    private Vocabulary getVocabulary(String fragment, Optional<String> namespace) {
        final URI uri = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        return termService.findVocabularyRequired(uri);
    }

    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/roots",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<TermDto> getAllRoots(@PathVariable String vocabularyIdFragment,
                                          @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace,
                                          @RequestParam(name = Constants.QueryParams.PAGE_SIZE, required = false) Integer pageSize,
                                          @RequestParam(name = Constants.QueryParams.PAGE, required = false) Integer pageNo,
                                          @RequestParam(name = "includeImported", required = false) boolean includeImported) {
        final Vocabulary vocabulary = getVocabulary(vocabularyIdFragment, namespace);
        final Pageable pageSpec = createPageRequest(pageSize, pageNo);
        return includeImported ? termService.findAllRootsIncludingImported(vocabulary, pageSpec) :
               termService.findAllRoots(vocabulary, pageSpec);
    }

    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ReadOnlyTerm getById(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                                @PathVariable("termIdFragment") String termIdFragment,
                                @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final URI termUri = getTermUri(vocabularyIdFragment, termIdFragment, namespace);
        return termService.findRequired(termUri);
    }

    private URI getTermUri(String vocabIdFragment, String termIdFragment, Optional<String> namespace) {
        return idResolver.resolveIdentifier(idResolver
                .buildNamespace(
                        resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), vocabIdFragment).toString(),
                        config.getNamespace().getTerm().getSeparator()), termIdFragment);
    }

    @GetMapping(value = "/vocabularies/{vocabularyIdFragment}/terms/{termIdFragment}/subterms",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<ReadOnlyTerm> getSubTerms(@PathVariable("vocabularyIdFragment") String vocabularyIdFragment,
                                          @PathVariable("termIdFragment") String termIdFragment,
                                          @RequestParam(name = Constants.QueryParams.NAMESPACE, required = false) Optional<String> namespace) {
        final ReadOnlyTerm parent = getById(vocabularyIdFragment, termIdFragment, namespace);
        return termService.findSubTerms(parent);
    }
}
