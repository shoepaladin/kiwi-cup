package app.kimitalk.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Structural contract tests over the widget resources. The provider classes
 * reference view ids and the widget-info XMLs reference layout files — a
 * rename on either side compiles fine but breaks taps on-device. These tests
 * pin both sides of that contract using plain JVM DOM parsing.
 */
public class LayoutContractTest {

    private static final String RES = "app/src/main/res";

    /**
     * Classes a RemoteViews layout may contain. A plain {@code <View>} is NOT
     * on this list — using one once made launchers refuse to add the widget
     * entirely ("couldn't add widget"). This test is the regression guard.
     */
    private static final Set<String> REMOTE_VIEWS_SAFE = new HashSet<>(Arrays.asList(
            "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
            "TextView", "ImageView", "ImageButton", "Button",
            "ProgressBar", "Chronometer", "AnalogClock",
            "ViewStub", "ViewFlipper",
            "ListView", "GridView", "StackView", "AdapterViewFlipper"));

    private static final String[] WIDGET_LAYOUTS = {"widget_mic", "widget_projects"};
    private static final String[] WIDGET_INFOS = {
            "kimi_mic_widget_info", "kimi_projects_widget_info"};

    private static Document parse(String path) throws Exception {
        File f = new File(path);
        assertTrue("missing resource: " + f.getAbsolutePath(), f.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Namespace-unaware on purpose: lets us read "android:id" literally.
        return factory.newDocumentBuilder().parse(f);
    }

    private static Set<String> collectIds(Document doc) {
        Set<String> ids = new HashSet<>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            String id = ((Element) all.item(i)).getAttribute("android:id");
            if (id.startsWith("@+id/")) {
                ids.add(id.substring("@+id/".length()));
            }
        }
        return ids;
    }

    private static int dp(String value) {
        assertTrue("expected a dp value, got: " + value, value.endsWith("dp"));
        return Integer.parseInt(value.substring(0, value.length() - 2));
    }

    // ---- The ids the providers click on must exist in the layouts ----

    @Test
    public void micLayoutExposesSingleButton() throws Exception {
        assertTrue(collectIds(parse(RES + "/layout/widget_mic.xml")).contains("widget_button"));
    }

    @Test
    public void projectsLayoutExposesAllControls() throws Exception {
        Set<String> ids = collectIds(parse(RES + "/layout/widget_projects.xml"));
        assertTrue(ids.contains("widget_root"));
        assertTrue(ids.contains("widget_btn_kimi"));
        assertTrue(ids.contains("widget_btn_settings"));
        assertTrue(ids.contains("pill_1"));
        assertTrue(ids.contains("pill_2"));
    }

    // ---- Every view class in a widget layout must be RemoteViews-safe ----

    @Test
    public void widgetLayoutsUseOnlyRemoteViewsSafeClasses() throws Exception {
        for (String layout : WIDGET_LAYOUTS) {
            NodeList all = parse(RES + "/layout/" + layout + ".xml").getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                String tag = ((Element) all.item(i)).getTagName();
                assertTrue(layout + " uses <" + tag + "> which RemoteViews cannot inflate",
                        REMOTE_VIEWS_SAFE.contains(tag));
            }
        }
    }

    // ---- Widget-info XMLs must reference real, sane layouts ----

    @Test
    public void providerInfoFilesReferenceExistingLayouts() throws Exception {
        for (String info : WIDGET_INFOS) {
            Element root = parse(RES + "/xml/" + info + ".xml").getDocumentElement();
            String initialLayout = root.getAttribute("android:initialLayout");
            assertTrue(info + " must declare an initialLayout",
                    initialLayout.startsWith("@layout/"));
            String layoutName = initialLayout.substring("@layout/".length());
            assertTrue(info + " references missing layout " + layoutName,
                    new File(RES + "/layout/" + layoutName + ".xml").isFile());
        }
    }

    @Test
    public void providerInfoDeclaresPositiveMinimumSizes() throws Exception {
        for (String info : WIDGET_INFOS) {
            Element root = parse(RES + "/xml/" + info + ".xml").getDocumentElement();
            assertTrue(info + " minWidth must be positive",
                    dp(root.getAttribute("android:minWidth")) > 0);
            assertTrue(info + " minHeight must be positive",
                    dp(root.getAttribute("android:minHeight")) > 0);
        }
    }

    @Test
    public void widgetsNeverPollInTheBackground() throws Exception {
        for (String info : WIDGET_INFOS) {
            Element root = parse(RES + "/xml/" + info + ".xml").getDocumentElement();
            // Static tap-targets must not wake the device to "update".
            assertEquals(info + " must not schedule periodic updates",
                    "0", root.getAttribute("android:updatePeriodMillis"));
        }
    }

    @Test
    public void previewImagesExistAndAreDistinct() throws Exception {
        // The picker preview is how users tell the two widgets apart.
        Set<String> previews = new HashSet<>();
        for (String info : WIDGET_INFOS) {
            Element root = parse(RES + "/xml/" + info + ".xml").getDocumentElement();
            String preview = root.getAttribute("android:previewImage");
            assertTrue(info + " must declare a previewImage", preview.startsWith("@drawable/"));
            String name = preview.substring("@drawable/".length());
            assertTrue(info + " references missing drawable " + name,
                    new File(RES + "/drawable/" + name + ".png").isFile());
            previews.add(name);
        }
        assertEquals("widgets must show distinct previews", WIDGET_INFOS.length, previews.size());
    }

    // ---- Sizing regression guards ----

    @Test
    public void projectsWidgetIsTwoCellsByOne() throws Exception {
        Element root = parse(RES + "/xml/kimi_projects_widget_info.xml").getDocumentElement();
        assertEquals(110, dp(root.getAttribute("android:minWidth")));   // 2 cells
        assertEquals(40, dp(root.getAttribute("android:minHeight")));   // 1 cell
        assertEquals("2", root.getAttribute("android:targetCellWidth"));
        assertEquals("1", root.getAttribute("android:targetCellHeight"));
    }

    @Test
    public void micWidgetStaysExactlyOneCell() throws Exception {
        Element root = parse(RES + "/xml/kimi_mic_widget_info.xml").getDocumentElement();
        assertEquals(40, dp(root.getAttribute("android:minWidth")));
        assertEquals(40, dp(root.getAttribute("android:minHeight")));
        assertEquals("1", root.getAttribute("android:targetCellWidth"));
        assertEquals("1", root.getAttribute("android:targetCellHeight"));
        // Belt and braces: never let a launcher stretch it on resize either.
        assertEquals(40, dp(root.getAttribute("android:minResizeWidth")));
        assertEquals(40, dp(root.getAttribute("android:minResizeHeight")));
    }
}
