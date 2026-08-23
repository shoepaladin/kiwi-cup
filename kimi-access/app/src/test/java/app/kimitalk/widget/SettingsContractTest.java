package app.kimitalk.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Pins for release metadata (shown in the About popup), the preference keys
 * (typos here silently corrupt settings), and URL normalization for
 * user-entered project links.
 */
public class SettingsContractTest {

    // ---- VersionInfo ----

    @Test
    public void versionNameIsSemver() {
        assertTrue(VersionInfo.VERSION_NAME.matches("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    public void versionDateIsIso8601() {
        assertTrue(VersionInfo.VERSION_DATE.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void repoUrlIsHttpsOnGitHub() {
        // Placeholder today, real repo soon — either way it must be a sane URL.
        assertTrue(VersionInfo.REPO_URL.startsWith("https://"));
        assertTrue(VersionInfo.REPO_URL.contains("github.com/"));
    }

    // ---- Branding ----

    @Test
    public void appIsNamedKimiAccess() throws Exception {
        // The rename pin: the app label shown by launchers and the About
        // popup both come from this string.
        File strings = new File("app/src/main/res/values/strings.xml");
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(strings);
        NodeList all = doc.getElementsByTagName("string");
        String appName = null;
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            if ("app_name".equals(el.getAttribute("name"))) {
                appName = el.getTextContent();
            }
        }
        assertEquals("Kimi Access", appName);
    }

    // ---- SettingsKeys ----

    @Test
    public void projectPreferenceKeysAreAllDistinct() {
        Set<String> keys = new HashSet<>();
        keys.add(SettingsKeys.KEY_P1_NAME);
        keys.add(SettingsKeys.KEY_P1_URL);
        keys.add(SettingsKeys.KEY_P2_NAME);
        keys.add(SettingsKeys.KEY_P2_URL);
        assertEquals("project preference keys collide", 4, keys.size());
    }

    // ---- URL normalization ----

    @Test
    public void normalizeUrlPrependsHttpsWhenSchemeMissing() {
        assertEquals("https://example.com/x", KimiTargets.normalizeUrl("example.com/x"));
    }

    @Test
    public void normalizeUrlKeepsExistingSchemes() {
        assertEquals("https://a.b/c", KimiTargets.normalizeUrl("https://a.b/c"));
        assertEquals("http://a.b/c", KimiTargets.normalizeUrl("http://a.b/c"));
    }

    @Test
    public void normalizeUrlTrimsWhitespace() {
        assertEquals("https://kimi.com", KimiTargets.normalizeUrl("  kimi.com  "));
    }

    @Test
    public void normalizeUrlFallsBackToKimiWebWhenEmpty() {
        assertEquals(KimiTargets.KIMI_WEB_URL, KimiTargets.normalizeUrl(""));
        assertEquals(KimiTargets.KIMI_WEB_URL, KimiTargets.normalizeUrl(null));
        assertEquals(KimiTargets.KIMI_WEB_URL, KimiTargets.normalizeUrl("   "));
    }
}
