package org.pac4j.core.run;

import org.pac4j.core.client.IndirectClient;
import org.pac4j.core.context.MockWebContext;
import org.pac4j.core.context.session.MockSessionStore;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.http.FoundAction;
import org.pac4j.core.profile.*;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.serializer.JavaSerializer;
import org.pac4j.core.util.TestsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Run manually a test for a client.
 *
 * @author Jerome Leleu
 * @since 1.9.0
 */
public abstract class RunClient implements TestsConstants {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public void run() {
        final var client = getClient();
        final var context = MockWebContext.create();
        final SessionStore sessionStore = new MockSessionStore();
        final var url = ((FoundAction) client.getRedirectionAction(context, sessionStore).get()).getLocation();
        logger.warn("Redirect to: \n{}", url);
        if (CommonHelper.isNotBlank(getLogin()) && CommonHelper.isNotBlank(getPassword())) {
            logger.warn("Use credentials: {} / {}", getLogin(), getPassword());
        } else {
            logger.warn("Use your own personal credentials");
        }
        if (canCancel()) {
            logger.warn("You can CANCEL the authentication.");
        }
        logger.warn("Returned url (copy/paste the fragment starting before the question mark of the query string):");
        final var scanner = new Scanner(System.in, StandardCharsets.UTF_8.name());
        final var returnedUrl = scanner.nextLine().trim();
        populateContextWithUrl(context, returnedUrl);
        final var credentials = client.getCredentials(context, sessionStore);
        if (credentials.isPresent()) {
            final var profile = client.getUserProfile(credentials.get(), context, sessionStore);
            logger.debug("userProfile: {}", profile);
            if (profile.isPresent() || !canCancel()) {
                verifyProfile((CommonProfile) profile.get());
                logger.warn("## Java serialization");
                final var javaSerializer = new JavaSerializer();
                var bytes = javaSerializer.serializeToBytes(profile.get());
                final var profile2 = (CommonProfile) javaSerializer.deserializeFromBytes(bytes);
                verifyProfile(profile2);
            }
        }
        logger.warn("################");
        logger.warn("Test successful!");
    }

    protected abstract String getLogin();

    protected abstract String getPassword();

    protected boolean canCancel() {
        return false;
    }

    protected abstract IndirectClient getClient();

    protected abstract void verifyProfile(final CommonProfile userProfile);

    protected void assertCommonProfile(final CommonProfile profile, final String email, final String firstName,
                                       final String familyName, final String displayName, final String username, final Gender gender,
                                       final Locale locale, final String pictureUrl, final String profileUrl, final String location) {
        assertEquals(email, profile.getEmail());
        assertEquals(firstName, profile.getFirstName());
        assertEquals(familyName, profile.getFamilyName());
        assertEquals(displayName, profile.getDisplayName());
        assertEquals(username, profile.getUsername());
        assertEquals(gender, profile.getGender());
        assertEquals(locale, profile.getLocale());
        if (pictureUrl == null) {
            assertNull(profile.getPictureUrl());
        } else {
            assertTrue(profile.getPictureUrl().toString().contains(pictureUrl));
        }
        if (profileUrl == null) {
            assertNull(profile.getProfileUrl());
        } else {
            final var profUrl = profile.getProfileUrl().toString();
            assertTrue(profUrl.startsWith(profileUrl));
        }
        assertEquals(location, profile.getLocation());
    }

    protected void populateContextWithUrl(final MockWebContext context, String url) {
        var pos = url.indexOf("?");
        if (pos >= 0) {
            url = url.substring(pos + 1);

            // removing stuffs after the hash for a regular query string
            pos = url.indexOf("#");
            if (pos >= 0) {
                url = url.substring(0, pos);
            }
        } else {
            // this is a hack to test client side stuffs, it would not work for server side
            pos = url.indexOf("#");
            if (pos >= 0) {
                url = url.substring(pos + 1);
            }
        }
        final Map<String, String> parameters = new HashMap<>();
        final var st = new StringTokenizer(url, "&");
        while (st.hasMoreTokens()) {
            final var keyValue = st.nextToken();
            final var parts = keyValue.split("=");
            if (parts != null && parts.length >= 2) {
                try {
                    parameters.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name()));
                } catch (final UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        context.addRequestParameters(parameters);
    }
}
