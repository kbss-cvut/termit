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
package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.UserAccount;

/**
 * Event emitted when a user exceeds the maximum number ({@link cz.cvut.kbss.termit.security.SecurityConstants#MAX_LOGIN_ATTEMPTS})
 * of unsuccessful login attempts.
 */
public class LoginAttemptsThresholdExceeded extends UserEvent {

    public LoginAttemptsThresholdExceeded(UserAccount user) {
        super(user);
    }
}
