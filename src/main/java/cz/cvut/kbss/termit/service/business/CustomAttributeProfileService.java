package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.CustomAttributesProfile;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.exception.importing.CustomAttributeConflictException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Manages custom attribute profiles.
 *
 * <p>A custom attribute profile is a set of {@link CustomAttribute} definitions together with a description of the
 * profile itself (identifier, title and description). Profiles are exchanged as JSON-LD, which allows transferring the
 * attribute definitions between TermIt instances.
 *
 * @see CustomAttributesProfile
 */
@Service
public class CustomAttributeProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomAttributeProfileService.class);

    private final DataRepositoryService dataService;

    public CustomAttributeProfileService(DataRepositoryService dataService) {
        this.dataService = dataService;
    }

    /**
     * Imports custom attributes from the specified profile.
     *
     * <p>Attributes which do not exist yet are created, existing attributes (matched by identifier) are updated with
     * the data from the profile. An existing attribute can be updated only when its domain and range are the same as in
     * the profile. Attributes not present in the profile are left untouched. The import is atomic, if any of the
     * attributes cannot be imported, nothing is imported.
     *
     * @param profile Profile containing the custom attributes to import
     * @throws CustomAttributeConflictException If an attribute from the profile already exists with a different domain
     *     or range
     * @throws ValidationException If any of the attributes in the profile is invalid
     */
    @Transactional
    public void importProfile(CustomAttributesProfile profile) {
        Objects.requireNonNull(profile);
        profile.getCustomAttributes().forEach(attribute -> {
            LOG.debug("Importing custom attribute <{}>", attribute.getUri());
            if (matchingExists(attribute)) {
                dataService.updateCustomAttribute(attribute);
            } else {
                dataService.persistCustomAttribute(attribute);
            }
        });
        LOG.debug(
                "Imported profile with {} custom attribute(s).",
                profile.getCustomAttributes().size());
    }

    private boolean matchingExists(CustomAttribute attribute) {
        Objects.requireNonNull(attribute.getUri(), "Custom Attribute must have an identifier!");
        Objects.requireNonNull(attribute.getRange(), "Custom Attribute must have a range!");
        Objects.requireNonNull(attribute.getDomain(), "Custom Attribute must have a domain!");
        return dataService
                .findCustomAttribute(attribute.getUri())
                .map(existing -> {
                    boolean domainMatches = attribute.getDomain().equals(existing.getDomain());
                    boolean rangeMatches = attribute.getRange().equals(existing.getRange());
                    if (domainMatches && rangeMatches) {
                        return true;
                    }
                    throw new CustomAttributeConflictException(rangeMatches, domainMatches, attribute.getUri());
                })
                .orElse(false);
    }
}
