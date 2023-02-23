package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.UserGroupService;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
@RestController
@RequestMapping(UserGroupController.PATH)
public class UserGroupController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(UserGroupController.class);

    static final String PATH = "/groups";

    private final UserGroupService groupService;

    public UserGroupController(IdentifierResolver idResolver, Configuration config, UserGroupService groupService) {
        super(idResolver, config);
        this.groupService = groupService;
    }

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<UserGroup> getAll() {
        return groupService.findAll();
    }

    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> create(@RequestBody UserGroup group) {
        groupService.persist(group);
        LOG.debug("Group {} created.", group);
        return ResponseEntity.created(generateLocation(group.getUri())).build();
    }

    @GetMapping(value = "/{fragment}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public UserGroup getById(@PathVariable String fragment) {
        final URI uri = resolveIdentifier(UserGroup.NAMESPACE, fragment);
        return groupService.findRequired(uri);
    }

    @DeleteMapping(value = "/{fragment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String fragment) {
        final URI uri = resolveIdentifier(UserGroup.NAMESPACE, fragment);
        final UserGroup toRemove = groupService.getRequiredReference(uri);
        groupService.remove(toRemove);
        LOG.debug("Group {} removed.", toRemove);
    }

    @PutMapping(value = "/{fragment}/label")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateGroupLabel(@PathVariable String fragment, @RequestBody String label) {
        final UserGroup group = getById(fragment);
        group.setLabel(label);
        groupService.update(group);
    }

    @PostMapping(value = "/{fragment}/members", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMembers(@PathVariable String fragment, @RequestBody Set<URI> toAdd) {
        final UserGroup target = getById(fragment);
        final List<User> usersToAdd = toAdd.stream().map(groupService::findRequiredUser)
                                           .collect(Collectors.toList());
        groupService.addMembers(target, usersToAdd);
        LOG.debug("{} users added to group {}.", usersToAdd.size(), target);
    }

    @DeleteMapping(value = "/{fragment}/members", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMembers(@PathVariable String fragment, @RequestBody Set<URI> toRemove) {
        final UserGroup target = getById(fragment);
        final List<User> usersToRemove = toRemove.stream().map(groupService::findRequiredUser)
                                                 .collect(Collectors.toList());
        groupService.removeMembers(target, usersToRemove);
        LOG.debug("{} users removed from group {}.", usersToRemove.size(), target);
    }
}
