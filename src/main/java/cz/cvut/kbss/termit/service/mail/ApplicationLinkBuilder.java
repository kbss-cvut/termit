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

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.FrontendPaths;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Builds links to application frontend.
 */
@Component
public class ApplicationLinkBuilder {

    private final String baseUrl;

    public ApplicationLinkBuilder(Configuration config) {
        this.baseUrl = config.getUrl();
    }

    /**
     * Creates a URL link to TermIt UI to a detail view of the specified asset.
     *
     * @param asset Asset to create link to
     * @return URL link to TermIt UI
     * @throws IllegalArgumentException If the specified asset has no matching frontend path configured
     */
    public String linkTo(Asset<?> asset) {
        Objects.requireNonNull(asset);
        final Map<String, Collection<String>> params = Collections.singletonMap(FrontendPaths.ACTIVE_TAB_PARAM,
                                                                                Collections.singleton(FrontendPaths.COMMENTS_TAB));
        return new AssetLink(baseUrl).createLink(asset, params);
    }
}
