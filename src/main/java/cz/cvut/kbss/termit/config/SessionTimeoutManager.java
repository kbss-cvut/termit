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
package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

@WebListener
public class SessionTimeoutManager implements HttpSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(SessionTimeoutManager.class);

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        LOG.trace("Created session {}.", se.getSession().getId());
        se.getSession().setMaxInactiveInterval(SecurityConstants.SESSION_TIMEOUT);
    }
}
