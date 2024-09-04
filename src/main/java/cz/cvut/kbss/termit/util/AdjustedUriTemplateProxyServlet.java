/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.mitre.dsmiley.httpproxy.URITemplateProxyServlet;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * A servlet wrapper which 1. throws away path info from the servlet request. 2. contains unique header keys (to avoid
 * Access-Control-Header-Origin duplication). 3. Uses Basic authentication in case repository username/password are
 * configured.
 */
public class AdjustedUriTemplateProxyServlet extends URITemplateProxyServlet {

    @Override
    protected void service(HttpServletRequest servletRequest,
                           HttpServletResponse servletResponse)
            throws ServletException, IOException {
        final String username = getConfigParam(ConfigParam.REPO_USERNAME.toString());
        final String password = getConfigParam(ConfigParam.REPO_PASSWORD.toString());
        super.service(new AuthenticatingServletRequestWrapper(servletRequest, username, password),
                new HttpServletResponseWrapper(servletResponse) {
                    @Override
                    public void addHeader(String name, String value) {
                        if (containsHeader(name)) {
                            return;
                        }
                        super.addHeader(name, value);
                    }
                });
    }

    static class AuthenticatingServletRequestWrapper extends HttpServletRequestWrapper {

        private final String username;
        private final String password;

        AuthenticatingServletRequestWrapper(HttpServletRequest request, String username, String password) {
            super(request);
            this.username = username;
            this.password = password;
        }

        @Override
        public String getPathInfo() {
            return "";
        }

        @Override
        public String getHeader(String name) {
            if (name.equalsIgnoreCase(HttpHeaders.AUTHORIZATION) && !username.isEmpty()) {
                return createBasicAuthentication();
            }
            return super.getHeader(name);
        }

        private String createBasicAuthentication() {
            String encoding = Base64.getEncoder()
                                    .encodeToString((username.concat(":").concat(password).getBytes()));
            return "Basic " + encoding;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name.equalsIgnoreCase(HttpHeaders.AUTHORIZATION) && !username.isEmpty()) {
                return Collections.enumeration(Collections.singletonList(createBasicAuthentication()));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            if (!username.isEmpty()) {
                List<String> temp = Collections.list(super.getHeaderNames());
                if (temp.stream().noneMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase)) {
                    temp.add(HttpHeaders.AUTHORIZATION);
                }
                return Collections.enumeration(temp);
            }
            return super.getHeaderNames();
        }
    }
}
