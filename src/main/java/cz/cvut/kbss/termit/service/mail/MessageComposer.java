package cz.cvut.kbss.termit.service.mail;

import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@Service
public class MessageComposer {

    private static final Logger LOG = LoggerFactory.getLogger(MessageComposer.class);

    private static final String TEMPLATES_DIRECTORY = "template/";

    private final Configuration config;

    private final VelocityEngine velocityEngine;

    public MessageComposer(Configuration config) {
        this.config = config;
        this.velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        velocityEngine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
    }

    /**
     * Composes a message content by loading the specified message template and setting values in it based on the
     * provided variables.
     *
     * @param templateName Message template name
     * @param variables    Variables to set in the template
     * @return Composed message content
     */
    public String composeMessage(String templateName, Map<String, Object> variables) {
        Objects.requireNonNull(templateName);
        Objects.requireNonNull(variables);
        final Template template = loadTemplate(templateName);
        final VelocityContext context = new VelocityContext(variables);
        final StringWriter stringWriter = new StringWriter();
        template.merge(context, stringWriter);
        return stringWriter.toString();
    }

    private Template loadTemplate(String templateName) {
        try {
            // First try loading a localized template
            return velocityEngine.getTemplate(
                    TEMPLATES_DIRECTORY + config.getPersistence().getLanguage() + "/" + templateName,
                    StandardCharsets.UTF_8.name());
        } catch (ResourceNotFoundException e) {
            // If we do not find it, fall back to the default one
            LOG.warn("Unable to find localized message template. Falling back to the default one.", e);
            return velocityEngine.getTemplate(TEMPLATES_DIRECTORY + Constants.DEFAULT_LANGUAGE + "/" + templateName,
                                              StandardCharsets.UTF_8.name());
        }
    }
}
