/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.mail;

import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.FrontendPaths;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates links to asset detail view in the frontend.
 * <p>
 * Note that it is assumed that the frontend uses fragment-based navigation.
 */
class AssetLink implements AssetVisitor {

    private final String baseUrl;
    private Map<String, Collection<String>> params;
    private UriComponents assetPath;

    protected AssetLink(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String createLink(Asset<?> asset, Map<String, Collection<String>> params) {
        Objects.requireNonNull(asset);
        Objects.requireNonNull(params);
        this.params = new HashMap<>(params);
        asset.accept(this);
        // We need to ensure that fragment is before query params, so first build the fragment with query params,
        // then build the whole URL
        return ServletUriComponentsBuilder.fromHttpUrl(baseUrl).fragment(assetPath.toUriString()).toUriString();
    }

    @Override
    public void visitTerm(AbstractTerm term) {
        final UriComponentsBuilder builder = ServletUriComponentsBuilder.fromPath(FrontendPaths.TERM_PATH)
                                                                        .queryParam(Constants.QueryParams.NAMESPACE,
                                                                                    IdentifierResolver.extractIdentifierNamespace(
                                                                                            term.getVocabulary()));
        params.forEach(builder::queryParam);
        this.assetPath = builder.buildAndExpand(IdentifierResolver.extractIdentifierFragment(term.getVocabulary()),
                                                IdentifierResolver.extractIdentifierFragment(term.getUri()));
    }

    @Override
    public void visitVocabulary(Vocabulary vocabulary) {
        final UriComponentsBuilder builder = ServletUriComponentsBuilder.fromPath(FrontendPaths.VOCABULARY_PATH)
                                                                        .queryParam(Constants.QueryParams.NAMESPACE,
                                                                                    IdentifierResolver.extractIdentifierNamespace(
                                                                                            vocabulary.getUri()));
        params.forEach(builder::queryParam);
        this.assetPath = builder.buildAndExpand(IdentifierResolver.extractIdentifierFragment(
                vocabulary.getUri()));
    }

    @Override
    public void visitResources(Resource resource) {
        throw new IllegalArgumentException("Links to resources are not supported!");
    }
}
