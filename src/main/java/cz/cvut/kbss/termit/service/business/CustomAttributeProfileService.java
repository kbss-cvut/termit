package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.CustomAttributesProfile;
import cz.cvut.kbss.termit.dto.CustomAttributesProfileDescription;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.exception.importing.CustomAttributeConflictException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manages custom attribute profiles.
 *
 * <p>A custom attribute profile is a set of {@link CustomAttribute} definitions together with a description of the
 * profile itself (identifier, title and description). Profiles are exchanged as JSON-LD, which allows transferring the
 * attribute definitions between TermIt instances.
 *
 * @see CustomAttributesProfile
 * @see CustomAttributesProfileDescription
 */
@Service
public class CustomAttributeProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomAttributeProfileService.class);

    private final DataRepositoryService dataService;

    private final JsonMapper jsonLdMapper;

    public CustomAttributeProfileService(
            DataRepositoryService dataService, @Qualifier("jsonLdMapper") JsonMapper jsonLdMapper) {
        this.dataService = dataService;
        this.jsonLdMapper = jsonLdMapper;
    }

    /**
     * Exports the specified custom attributes as a JSON-LD profile.
     *
     * <p>The resulting profile consists of the provided description and the definitions of the custom attributes with
     * the specified identifiers.
     *
     * @param profileDescription Description of the exported profile (its identifier, title and description)
     * @param customAttributeUris Identifiers of the custom attributes to include in the profile
     * @return Resource containing the profile serialized as JSON-LD
     */
    @Transactional(readOnly = true)
    public TypeAwareResource exportProfile(
            CustomAttributesProfileDescription profileDescription, Set<URI> customAttributeUris) {
        LOG.debug("Exporting profile with {} custom attribute(s).", customAttributeUris.size());
        final List<CustomAttribute> attributes = dataService.findCustomAttributes(customAttributeUris);
        final CustomAttributesProfile profile = new CustomAttributesProfile(profileDescription, Set.copyOf(attributes));
        return new TypeAwareByteArrayResource(jsonLdMapper.writeValueAsBytes(profile), JsonLd.MEDIA_TYPE, ".jsonld");
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
        Objects.requireNonNull(attribute.getUri());
        return dataService
                .findCustomAttribute(attribute.getUri())
                .map(existing -> {
                    boolean domainMatches = attribute.getDomain().equals(existing.getDomain());
                    boolean rangeMatches = attribute.getRange().equals(existing.getRange());
                    if (domainMatches && rangeMatches) {
                        return true;
                    }
                    throw new CustomAttributeConflictException(domainMatches, rangeMatches, attribute.getUri());
                })
                .orElse(false);
    }
}
