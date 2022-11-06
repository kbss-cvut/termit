package cz.cvut.kbss.termit.service.mail;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@Service
public class MessageComposer {

    private static final String TEMPLATES_DIRECTORY = "template/";

    private final VelocityEngine velocityEngine;

    public MessageComposer() {
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
        final Template template = velocityEngine.getTemplate(TEMPLATES_DIRECTORY + templateName,
                                                             StandardCharsets.UTF_8.name());
        final VelocityContext context = new VelocityContext(variables);
        final StringWriter stringWriter = new StringWriter();
        template.merge(context, stringWriter);
        return stringWriter.toString();
    }
}
