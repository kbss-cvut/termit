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
package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;

import java.util.Objects;

/**
 * Base class for user-related events.
 */
abstract class UserEvent {

    private final UserAccount user;

    UserEvent(UserAccount user) {
        this.user = Objects.requireNonNull(user);
    }

    /**
     * Gets the user who is concerned by this event.
     *
     * @return User
     */
    public UserAccount getUser() {
        return user;
    }
}
