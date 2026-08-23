package app.kimitalk.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Structural tests over AndroidManifest.xml. The manifest is where widget
 * registrations most often rot, so the critical contract (widget receiver,
 * trampoline activity, package visibility) is pinned here and verified by
 * plain JVM DOM parsing — no Android runtime needed.
 */
public class ManifestTest {

    private static Document doc;

    @BeforeClass
    public static void loadManifest() throws Exception {
        File manifest = new File("app/src/main/AndroidManifest.xml");
        assertTrue("manifest must exist at " + manifest.getAbsolutePath(), manifest.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Namespace-unaware on purpose: lets us read "android:name" literally.
        doc = factory.newDocumentBuilder().parse(manifest);
    }

    private static Element first(String tag) {
        NodeList list = doc.getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static boolean hasAttrValue(NodeList elements, String attr, String value) {
        for (int i = 0; i < elements.getLength(); i++) {
            if (value.equals(((Element) elements.item(i)).getAttribute(attr))) {
                return true;
            }
        }
        return false;
    }

    private static Element findByName(NodeList elements, String androidName) {
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            if (androidName.equals(el.getAttribute("android:name"))) {
                return el;
            }
        }
        return null;
    }

    @Test
    public void packageIdIsStable() {
        assertEquals("app.kimitalk.widget", doc.getDocumentElement().getAttribute("package"));
    }

    @Test
    public void targetsModernAndroidWithWideCompatibility() {
        Element sdk = first("uses-sdk");
        assertNotNull(sdk);
        assertEquals("23", sdk.getAttribute("android:minSdkVersion"));
        assertEquals("35", sdk.getAttribute("android:targetSdkVersion"));
    }

    @Test
    public void declaresKimiPackageVisibilityForAndroid11Plus() {
        Element queries = first("queries");
        assertNotNull("<queries> block required for package visibility", queries);
        assertTrue(
                "<queries> must list the Kimi package",
                hasAttrValue(queries.getElementsByTagName("package"),
                        "android:name", KimiTargets.KIMI_PACKAGE));
        assertTrue(
                "<queries> must declare an https VIEW intent so browsers are visible",
                hasAttrValue(queries.getElementsByTagName("data"),
                        "android:scheme", "https"));
    }

    @Test
    public void bothWidgetReceiversAreRegisteredExportedAndWired() {
        String[] expected = {".KimiProjectsWidgetProvider", ".KimiMicWidgetProvider"};
        NodeList receivers = doc.getElementsByTagName("receiver");
        assertEquals("both widget variants must be registered", expected.length, receivers.getLength());

        for (String name : expected) {
            Element receiver = findByName(receivers, name);
            assertNotNull("missing receiver " + name, receiver);
            assertEquals("true", receiver.getAttribute("android:exported"));
            assertTrue(
                    name + " must listen for APPWIDGET_UPDATE",
                    hasAttrValue(receiver.getElementsByTagName("action"),
                            "android:name", "android.appwidget.action.APPWIDGET_UPDATE"));
            assertTrue(
                    name + " must reference an appwidget-provider XML",
                    hasAttrValue(receiver.getElementsByTagName("meta-data"),
                            "android:name", "android.appwidget.provider"));
        }
    }

    @Test
    public void widgetReceiversHaveDistinctLabels() {
        // Identical picker labels made the two widgets indistinguishable —
        // users added the 2x1 thinking it was the 1x1. Never again.
        NodeList receivers = doc.getElementsByTagName("receiver");
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < receivers.getLength(); i++) {
            labels.add(((Element) receivers.item(i)).getAttribute("android:label"));
        }
        assertEquals("widget receivers must have distinct picker labels",
                receivers.getLength(), labels.size());
    }

    @Test
    public void manifestVersionMatchesVersionInfo() {
        // The About popup reads VersionInfo; the store/installer reads the
        // manifest. They must never drift apart.
        assertEquals(VersionInfo.VERSION_NAME,
                doc.getDocumentElement().getAttribute("android:versionName"));
    }

    @Test
    public void settingsActivityIsTheOnlyLauncherEntry() {
        Element settings = findByName(doc.getElementsByTagName("activity"), ".SettingsActivity");
        assertNotNull("settings activity must be registered", settings);
        assertEquals("true", settings.getAttribute("android:exported"));
        assertTrue(
                "settings activity must be the LAUNCHER entry",
                hasAttrValue(settings.getElementsByTagName("category"),
                        "android:name", "android.intent.category.LAUNCHER"));
    }

    @Test
    public void internalActivitiesAreNotExported() {
        String[] internal = {".LaunchActivity", ".ChooseTargetActivity"};
        NodeList activities = doc.getElementsByTagName("activity");
        for (String name : internal) {
            Element activity = findByName(activities, name);
            assertNotNull("missing activity " + name, activity);
            assertEquals(name + " must not be exported",
                    "false", activity.getAttribute("android:exported"));
        }
    }
}
