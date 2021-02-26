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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.rest.util.RestUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Provides access to general data from repository.
 * <p>
 * Note that this endpoint is currently not secured.
 */
@RestController
@RequestMapping("/data")
public class DataController {

    private static final Logger LOG = LoggerFactory.getLogger(DataController.class);

    private final DataRepositoryService dataService;

    @Autowired
    public DataController(DataRepositoryService dataService) {
        this.dataService = dataService;
    }

    @RequestMapping(value = "/properties", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<RdfsResource> getProperties() {
        return dataService.findAllProperties();
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    @RequestMapping(value = "/properties", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createProperty(@RequestBody RdfsResource property) {
        dataService.persistProperty(property);
        LOG.debug("Created property {}.", property);
        return ResponseEntity.created(RestUtils.createLocationFromCurrentUri()).build();
    }

    /**
     * Gets basic metadata for a RDFS resource with the specified identifier.
     *
     * @param id Resource identifier
     * @return Metadata
     */
    @RequestMapping(value = "/resource", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public RdfsResource getById(@RequestParam("iri") URI id) {
        return dataService.find(id).orElseThrow(() -> NotFoundException.create("Resource", id));
    }

    @PreAuthorize("permitAll()")
    @RequestMapping(value = "/label", method = RequestMethod.GET)
    public String getLabel(@RequestParam("iri") URI id) {
        return dataService.getLabel(id).orElseThrow(
                () -> new NotFoundException("Resource with id " + id + " not found or it has no matching label."));
    }
}
