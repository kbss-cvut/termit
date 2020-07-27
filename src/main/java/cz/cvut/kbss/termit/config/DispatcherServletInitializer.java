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
package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.rest.servlet.DiagnosticsContextFilter;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.util.Constants;
import net.bull.javamelody.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.*;
import java.util.Collections;
import java.util.EnumSet;

/**
 * Entry point of the application invoked by the application server on deploy.
 */
public class DispatcherServletInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(DispatcherServletInitializer.class);

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{AppConfig.class};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[]{};
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{Constants.REST_MAPPING_PATH + "/*"};
    }

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        super.onStartup(servletContext);
        printStartupMessage();

        initSecurityFilter(servletContext);
        initMdcFilter(servletContext);
        initUTF8Filter(servletContext);
        secureMelodyMonitoring(servletContext);
        servletContext.addListener(new RequestContextListener());
    }

    private static void printStartupMessage() {
        final String msg = "* TermIt " + Constants.VERSION + " *";
        LOG.info(String.join("", Collections.nCopies(msg.length(), "*")));
        LOG.info(msg);
        LOG.info(String.join("", Collections.nCopies(msg.length(), "*")));
    }

    /**
     * Initializes Spring Security servlet filter
     */
    private static void initSecurityFilter(ServletContext servletContext) {
        FilterRegistration.Dynamic securityFilter = servletContext.addFilter("springSecurityFilterChain",
                DelegatingFilterProxy.class);
        final EnumSet<DispatcherType> es = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD);
        securityFilter.addMappingForUrlPatterns(es, true, "/*");
    }

    /**
     * Initializes diagnostics context filter for logging session info
     */
    private static void initMdcFilter(ServletContext servletContext) {
        FilterRegistration.Dynamic mdcFilter = servletContext
                .addFilter("diagnosticsContextFilter", new DiagnosticsContextFilter());
        final EnumSet<DispatcherType> es = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD);
        mdcFilter.addMappingForUrlPatterns(es, true, "/*");
    }

    /**
     * Initializes UTF-8 encoding filter
     */
    private static void initUTF8Filter(ServletContext servletContext) {
        FilterRegistration.Dynamic mdcFilter = servletContext
                .addFilter("urlEncodingFilter", new CharacterEncodingFilter());
        mdcFilter.setInitParameter("encoding", "UTF-8");
        mdcFilter.setInitParameter("forceEncoding", "true");
        final EnumSet<DispatcherType> es = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD);
        mdcFilter.addMappingForUrlPatterns(es, true, "/*");
    }

    private static void secureMelodyMonitoring(ServletContext servletContext) {
        final FilterRegistration registration = servletContext.getFilterRegistration("javamelody");
        if (registration == null) {
            return;
        }
        // Allows access only to admin user
        registration
                .setInitParameter(Parameter.AUTHORIZED_USERS.getCode(), SecurityConstants.MONITORING_USER_CREDENTIALS);
    }

    @Override
    protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        registration.setMultipartConfig(getMultipartConfigElement());
    }

    /**
     * Configures multipart processing (for file upload).
     */
    private static MultipartConfigElement getMultipartConfigElement() {
        return new MultipartConfigElement(Constants.UPLOADED_FILE_LOCATION, Constants.MAX_UPLOADED_FILE_SIZE,
                Constants.MAX_UPLOAD_REQUEST_SIZE, Constants.UPLOADED_FILE_SIZE_THRESHOLD);
    }
}
