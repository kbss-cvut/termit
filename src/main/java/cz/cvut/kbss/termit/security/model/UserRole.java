/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.security.model;

import cz.cvut.kbss.termit.util.Vocabulary;

import static cz.cvut.kbss.termit.security.SecurityConstants.ROLE_ADMIN;
import static cz.cvut.kbss.termit.security.SecurityConstants.ROLE_USER;

/**
 * Represents user roles in the system.
 * <p>
 * These roles are used for basic system authorization.
 */
public enum UserRole {

    /**
     * Regular application user.
     * <p>
     * Does not map to any specific subclass of {@link Vocabulary#s_c_uzivatel_termitu}.
     */
    USER("", ROLE_USER),
    /**
     * Application administrator.
     * <p>
     * Maps to {@link Vocabulary#s_c_administrator_termitu}.
     */
    ADMIN(Vocabulary.s_c_administrator_termitu, ROLE_ADMIN);

    private final String type;
    private final String name;

    UserRole(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    /**
     * Checks whether a role for the specified type exists.
     *
     * @param type The type to check
     * @return Role existence info
     */
    public static boolean exists(String type) {
        for (UserRole r : values()) {
            if (r.type.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets role for the specified ontological type.
     *
     * @param type Type to get role for
     * @return Matching role
     * @throws IllegalArgumentException If no matching role exists
     */
    public static UserRole fromType(String type) {
        for (UserRole r : values()) {
            if (r.type.equals(type)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No role found for type " + type + ".");
    }

    /**
     * Gets role for the specified role name.
     *
     * @param roleName Role name
     * @return Matching role
     * @throws IllegalArgumentException If no matching role exists
     */
    public static UserRole fromRoleName(String roleName) {
        for (UserRole r : values()) {
            if (r.name.equals(roleName)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No role found for name " + roleName + ".");
    }
}
