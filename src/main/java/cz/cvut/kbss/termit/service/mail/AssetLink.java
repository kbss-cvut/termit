package cz.cvut.kbss.termit.service.mail;

import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.FrontendPaths;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

class AssetLink implements AssetVisitor {

    private final String baseUrl;
    private UriComponentsBuilder builder;
    private UriComponents result;

    protected AssetLink(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String createLink(Asset<?> asset, Map<String, Collection<String>> params) {
        Objects.requireNonNull(asset);
        Objects.requireNonNull(params);
        this.builder = ServletUriComponentsBuilder.fromHttpUrl(baseUrl);
        params.forEach(builder::queryParam);
        asset.accept(this);
        return result.toString();
    }

    @Override
    public void visitTerm(AbstractTerm term) {
        this.result = builder.path(FrontendPaths.TERM_PATH)
                             .buildAndExpand(IdentifierResolver.extractIdentifierFragment(term.getVocabulary()), IdentifierResolver.extractIdentifierFragment(term.getUri()), IdentifierResolver.extractIdentifierNamespace(term.getVocabulary()));
    }

    @Override
    public void visitVocabulary(Vocabulary vocabulary) {
        this.result = builder.path(FrontendPaths.VOCABULARY_PATH)
                             .buildAndExpand(IdentifierResolver.extractIdentifierFragment(vocabulary.getUri()), IdentifierResolver.extractIdentifierNamespace(vocabulary.getUri()));
    }

    @Override
    public void visitResources(Resource resource) {
        throw new IllegalArgumentException("Links to resources are not supported!");
    }
}
