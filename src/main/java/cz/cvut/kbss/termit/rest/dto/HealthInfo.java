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
