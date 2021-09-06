/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.service.document.html.HtmlTermOccurrenceResolver;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

/**
 * Utility class providing instances of prototype-scoped {@link TermOccurrenceResolver} implementations.
 * <p>
 * Since the beans are prototype-scoped, each call to the methods will produce a new instance of the corresponding
 * class.
 */
@Component
interface TermOccurrenceResolvers {

    @Lookup
    HtmlTermOccurrenceResolver htmlTermOccurrenceResolver();
}
