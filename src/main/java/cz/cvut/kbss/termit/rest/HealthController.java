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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.rest.dto.HealthInfo;
import cz.cvut.kbss.termit.service.jmx.AppAdminBean;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "Application health API.")
@RestController
public class HealthController {

    private final AppAdminBean adminBean;

    public HealthController(AppAdminBean adminBean) {
        this.adminBean = adminBean;
    }

    @Operation(description = "Gets basic status of the health of the application.")
    @ApiResponse(responseCode = "200", description = "Basic application health data.")
    @GetMapping(value = "health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthInfo health() {
        return adminBean.getHealthInfo();
    }
}
