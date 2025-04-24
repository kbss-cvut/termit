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
package cz.cvut.kbss.termit.service.document;

import java.time.Instant;
import java.util.Optional;

/**
 * Specifies what resource content should be retrieved.
 *
 * @param at                            Timestamp indicating the version of the resource to retrieve
 * @param withoutUnconfirmedOccurrences Whether the content should not contain unconfirmed term occurrences
 */
public record ResourceRetrievalSpecification(Optional<Instant> at, boolean withoutUnconfirmedOccurrences) {
}
