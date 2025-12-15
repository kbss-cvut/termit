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
package cz.cvut.kbss.termit.security;

/**
 * Security-related constants.
 */
public class SecurityConstants {

    /**
     * Username parameter for the login form
     */
    public static final String USERNAME_PARAM = "username";

    /**
     * Password parameter for the login form
     */
    public static final String PASSWORD_PARAM = "password";

    /**
     * URL path used for logging into the application.
     */
    public static final String LOGIN_PATH = "/login";

    /**
     * URL path used for logging out of the application.
     */
    public static final String LOGOUT_PATH = "/logout";

    /**
     * String prefix added to JWT tokens in the Authorization header.
     */
    public static final String JWT_TOKEN_PREFIX = "Bearer ";

    /**
     * JWT claim used to store user's global roles in the system.
     */
    public static final String JWT_ROLE_CLAIM = "role";

    /**
     * JWT claim used to store the type of the JWT token.
     * The claim in the body is a copy of the {@code typ} header.
     */
    public static final String JWT_TYP_CLAIM = "typ";

    /**
     * Default algorithm used for JWT private key
     */
    public static final String JWT_DEFAULT_KEY_ALGORITHM = "HS256";

    /**
     * Delimiter used to separate roles in a JWT.
     */
    public static final String JWT_ROLE_DELIMITER = "-";

    /**
     * Session timeout in milliseconds. 24 hours.
     */
    public static final int SESSION_TIMEOUT = 24 * 60 * 60 * 1000;

    /**
     * Maximum number of unsuccessful login attempts.
     */
    public static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * System administrator role
     */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * Full user role
     */
    public static final String ROLE_FULL_USER = "ROLE_FULL_USER";

    /**
     * Restricted user role
     */
    public static final String ROLE_RESTRICTED_USER = "ROLE_RESTRICTED_USER";

    /**
     * Anonymous user role (not logged-in user)
     */
    public static final String ROLE_ANONYMOUS_USER = "ROLE_ANONYMOUS_USER";

    /**
     * Path of REST endpoints which are not secured.
     */
    public static final String PUBLIC_API_PATH = "/public";

    private SecurityConstants() {
        throw new AssertionError();
    }
}
