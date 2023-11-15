/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.security;

import cz.cvut.kbss.termit.event.LoginFailureEvent;
import cz.cvut.kbss.termit.event.LoginSuccessEvent;
import org.springframework.context.event.EventListener;

/**
 * Tracks login attempts.
 */
public interface LoginTracker {

    /**
     * Registers an unsuccessful login attempt by the specified user.
     * <p>
     * This basically means that the user entered an incorrect password.
     *
     * @param event Event representing the login attempt
     */
    @EventListener
    void onLoginFailure(LoginFailureEvent event);

    /**
     * Registers a successful login attempt by the specified user.
     * <p>
     * This basically means that the user entered the correct password and will be logged in.
     *
     * @param event Event representing the login attempt
     */
    @EventListener
    void onLoginSuccess(LoginSuccessEvent event);
}
