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
package cz.cvut.kbss.termit.rest.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents health info of the application.
 */
public class HealthInfo {

    /**
     * Health status of the whole application
     */
    private HealthStatus status;

    /**
     * Statuses of system components (e.g., repository, text analysis service).
     */
    private Map<String, HealthStatus> components = new HashMap<>();

    private HealthInfo(HealthStatus status) {
        this.status = status;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public Map<String, HealthStatus> getComponents() {
        return components;
    }

    public void setComponents(Map<String, HealthStatus> components) {
        this.components = components;
    }

    public void setComponentStatus(String component, HealthStatus status) {
        components.put(component, status);
    }

    @Override
    public String toString() {
        return "HealthInfo{" +
                "status=" + status +
                ", components=" + components +
                '}';
    }

    public static HealthInfo up() {
        return new HealthInfo(HealthStatus.UP);
    }

    public static HealthInfo down() {
        return new HealthInfo(HealthStatus.DOWN);
    }
}
