package cz.cvut.kbss.termit.dto;

import java.io.Serializable;

/**
 * Represents configuration data provided by the server to client.
 */
public class ConfigurationDto implements Serializable {

    private String language;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
