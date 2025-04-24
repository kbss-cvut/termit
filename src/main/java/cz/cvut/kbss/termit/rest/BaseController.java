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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;

import java.net.URI;
import java.util.Objects;

import static cz.cvut.kbss.termit.util.Constants.QueryParams;

/**
 * Base for application REST controllers.
 * <p>
 * Will be used to define general security for the public API.
 */
public class BaseController {

    protected final IdentifierResolver idResolver;

    protected final Configuration config;

    protected BaseController(IdentifierResolver idResolver, Configuration config) {
        this.idResolver = idResolver;
        this.config = config;
    }

    /**
     * Resolves identifier based on the specified resource (if provided) or the namespace loaded from application
     * configuration.
     *
     * @param namespace       Explicitly provided namespace. Optional
     * @param fragment        Locally unique identifier fragment
     * @return Resolved identifier
     */
    protected URI resolveIdentifier(String namespace, String fragment) {
        return idResolver.resolveIdentifier(namespace, fragment);
    }

    URI generateLocation(URI identifier) {
        return generateLocation(identifier, null);
    }

    URI generateLocation(URI identifier, String namespace) {
        if (Objects.equals(IdentifierResolver.extractIdentifierNamespace(identifier), namespace)) {
            return RestUtils.createLocationFromCurrentUriWithPath("/{name}",
                    IdentifierResolver.extractIdentifierFragment(identifier));
        } else {
            return RestUtils.createLocationFromCurrentUriWithPathAndQuery("/{name}", QueryParams.NAMESPACE,
                    IdentifierResolver.extractIdentifierNamespace(identifier),
                    IdentifierResolver.extractIdentifierFragment(identifier));
        }
    }

    /**
     * Ensures that the entity specified for update has the same identifier as the one that has been resolved from the
     * request URL.
     *
     * @param entity            Entity
     * @param requestIdentifier Identifier resolved from request
     */
    void verifyRequestAndEntityIdentifier(HasIdentifier entity, URI requestIdentifier) {
        if (!requestIdentifier.equals(entity.getUri())) {
            throw new ValidationException(
                    "The ID " + requestIdentifier +
                            ", resolved from request URL, does not match the ID of the specified entity.");
        }
    }
}
