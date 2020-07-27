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
package cz.cvut.kbss.termit.util;

import org.springframework.core.env.Environment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents application-wide configuration.
 * <p>
 * The runtime configuration consists of predefined default values and configuration loaded from config files on
 * classpath. Values from config files supersede the default values.
 */
public class Configuration {

    /**
     * Contains default values for some of the configuration parameters.
     * <p>
     * These defaults are used when the parameters are not specified
     */
    private static final Map<ConfigParam, String> DEFAULTS = initDefaults();

    private final Environment environment;

    public Configuration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Gets the value of the specified parameter.
     *
     * @param param Parameter
     * @return Value of the parameter
     * @throws IllegalStateException If the parameter value is not set and it has no default value
     */
    public String get(ConfigParam param) {
        Objects.requireNonNull(param);
        if (!environment.containsProperty(param.toString())) {
            if (DEFAULTS.containsKey(param)) {
                return DEFAULTS.get(param);
            }
            throw new IllegalStateException("Value of key '" + param + "' not configured.");
        }
        return environment.getProperty(param.toString());
    }

    /**
     * Gets the value of the specified parameter.
     *
     * @param param        Parameter
     * @param defaultValue Value to return if the parameter is not set
     * @return Value of the parameter or the default value
     */
    public String get(ConfigParam param, String defaultValue) {
        Objects.requireNonNull(param);
        return environment.getProperty(param.toString(), defaultValue);
    }

    /**
     * Checks whether the configuration contains the specified parameter.
     *
     * @param param Parameter
     * @return Whether the specified parameter is configured
     */
    public boolean contains(ConfigParam param) {
        Objects.requireNonNull(param);
        return environment.containsProperty(param.toString());
    }

    /**
     * Checks whether the specified parameter is set to {@code true}.
     * <p>
     * If the parameter value is not configured, {@code false} is returned.
     *
     * @param param Parameter
     * @return Boolean value of the parameter
     */
    public boolean is(ConfigParam param) {
        Objects.requireNonNull(param);
        return Boolean.parseBoolean(get(param, Boolean.FALSE.toString()));
    }

    private static Map<ConfigParam, String> initDefaults() {
        final Map<ConfigParam, String> map = new EnumMap<>(ConfigParam.class);
        map.put(ConfigParam.LANGUAGE, Constants.DEFAULT_LANGUAGE);
        map.put(ConfigParam.ADMIN_CREDENTIALS_LOCATION, System.getProperty("user.home"));
        map.put(ConfigParam.ADMIN_CREDENTIALS_FILE, Constants.ADMIN_CREDENTIALS_FILE);
        map.put(ConfigParam.TERM_NAMESPACE_SEPARATOR, Constants.DEFAULT_TERM_NAMESPACE_SEPARATOR);
        map.put(ConfigParam.CHANGE_TRACKING_CONTEXT_EXTENSION, Constants.DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION);
        return map;
    }
}
