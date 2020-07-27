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
package cz.cvut.kbss.termit.util;

import cz.cvut.kbss.ontodriver.sesame.SesameDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    private MockEnvironment env;

    private Configuration sut;

    @BeforeEach
    void setUp() {
        this.env = new MockEnvironment();
        this.sut = new Configuration(env);
    }

    @Test
    void getReturnsMatchingPropertyValue() {
        final String driver = SesameDataSource.class.getName();
        env.setProperty(ConfigParam.DRIVER.toString(), driver);
        assertEquals(driver, sut.get(ConfigParam.DRIVER));
    }

    @Test
    void getReturnsPredefinedDefaultValueWhenItExists() {
        assertEquals(Constants.DEFAULT_LANGUAGE, sut.get(ConfigParam.LANGUAGE));
    }

    @Test
    void getThrowsIllegalStateExceptionWhenPropertyIsNotConfiguredAndHasNoDefault() {
        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> sut.get(ConfigParam.DRIVER));
        assertEquals("Value of key \'" + ConfigParam.DRIVER + "\' not configured.", ex.getMessage());
    }

    @Test
    void getWithDefaultReturnsMatchingPropertyValue() {
        final String driver = SesameDataSource.class.getName();
        env.setProperty(ConfigParam.DRIVER.toString(), driver);
        assertEquals(driver, sut.get(ConfigParam.DRIVER, "default"));
    }

    @Test
    void getWithDefaultReturnsDefaultValueWhenPropertyIsNotConfigured() {
        final String driver = SesameDataSource.class.getName();
        assertEquals(driver, sut.get(ConfigParam.DRIVER, driver));
    }

    @Test
    void containsReturnsTrueWhenPropertyIsConfigured() {
        final String driver = SesameDataSource.class.getName();
        env.setProperty(ConfigParam.DRIVER.toString(), driver);
        assertTrue(sut.contains(ConfigParam.DRIVER));
    }

    @Test
    void containsReturnsFalseWhenPropertyIsNotConfigured() {
        assertFalse(sut.contains(ConfigParam.DRIVER));
    }

    @Test
    void isReturnsTrueWhenPropertyIsSetToTrue() {
        // We're abusing the DRIVER parameter here a little, since it normally expects a string, not a boolean
        env.setProperty(ConfigParam.DRIVER.toString(), Boolean.TRUE.toString());
        assertTrue(sut.is(ConfigParam.DRIVER));
    }

    @Test
    void isReturnsFalseWhenPropertyIsNotConfigured() {
        assertFalse(sut.is(ConfigParam.DRIVER));
    }

    @Test
    void containsSensibleDefaults() {
        assertEquals(Constants.DEFAULT_LANGUAGE, sut.get(ConfigParam.LANGUAGE));
        assertEquals(System.getProperty("user.home"), sut.get(ConfigParam.ADMIN_CREDENTIALS_LOCATION));
        assertEquals(Constants.ADMIN_CREDENTIALS_FILE, sut.get(ConfigParam.ADMIN_CREDENTIALS_FILE));
        assertEquals(Constants.DEFAULT_TERM_NAMESPACE_SEPARATOR, sut.get(ConfigParam.TERM_NAMESPACE_SEPARATOR));
    }
}
