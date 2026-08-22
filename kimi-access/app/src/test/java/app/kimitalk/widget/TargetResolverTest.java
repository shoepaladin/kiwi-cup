package app.kimitalk.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.kimitalk.widget.TargetResolver.Action;
import app.kimitalk.widget.TargetResolver.Mode;
import app.kimitalk.widget.TargetResolver.Plan;

/**
 * Hermetic JVM tests for the tap-routing decision logic. No Android runtime
 * required — TargetResolver is pure Java by design.
 */
public class TargetResolverTest {

    // ---- AUTO mode ----

    @Test
    public void autoPrefersKimiAppWhenBothAvailable() {
        Plan plan = TargetResolver.resolve(Mode.AUTO, true, true);
        assertEquals(Action.KIMI_APP, plan.action);
        assertEquals(KimiTargets.KIMI_PACKAGE, plan.payload);
    }

    @Test
    public void autoPrefersKimiAppEvenWithoutBrowser() {
        Plan plan = TargetResolver.resolve(Mode.AUTO, true, false);
        assertEquals(Action.KIMI_APP, plan.action);
    }

    @Test
    public void autoFallsBackToWebChatWhenAppMissing() {
        Plan plan = TargetResolver.resolve(Mode.AUTO, false, true);
        assertEquals(Action.WEB_CHAT, plan.action);
        assertEquals(KimiTargets.KIMI_WEB_URL, plan.payload);
    }

    @Test
    public void autoReportsUnavailableWhenNothingCanHandleTap() {
        Plan plan = TargetResolver.resolve(Mode.AUTO, false, false);
        assertEquals(Action.UNAVAILABLE, plan.action);
        assertNull(plan.payload);
    }

    // ---- APP mode (forced app, e.g. split-widget left zone) ----

    @Test
    public void appModeLaunchesAppWhenInstalled() {
        Plan plan = TargetResolver.resolve(Mode.APP, true, true);
        assertEquals(Action.KIMI_APP, plan.action);
    }

    @Test
    public void appModeFallsBackToWebWhenAppMissing() {
        Plan plan = TargetResolver.resolve(Mode.APP, false, true);
        assertEquals(Action.WEB_CHAT, plan.action);
    }

    // ---- WEB mode (forced web, e.g. split-widget right zone) ----

    @Test
    public void webModePrefersBrowserEvenWhenAppInstalled() {
        Plan plan = TargetResolver.resolve(Mode.WEB, true, true);
        assertEquals(Action.WEB_CHAT, plan.action);
        assertEquals(KimiTargets.KIMI_WEB_URL, plan.payload);
    }

    @Test
    public void webModeFallsBackToAppWhenNoBrowser() {
        Plan plan = TargetResolver.resolve(Mode.WEB, true, false);
        assertEquals(Action.KIMI_APP, plan.action);
    }

    // ---- ASK mode ----

    @Test
    public void askModePromptsOnlyWhenBothTargetsAvailable() {
        Plan plan = TargetResolver.resolve(Mode.ASK, true, true);
        assertEquals(Action.ASK_USER, plan.action);
        assertNull(plan.payload);
    }

    @Test
    public void askModeSkipsPromptWhenOnlyAppExists() {
        assertEquals(Action.KIMI_APP, TargetResolver.resolve(Mode.ASK, true, false).action);
    }

    @Test
    public void askModeSkipsPromptWhenOnlyBrowserExists() {
        assertEquals(Action.WEB_CHAT, TargetResolver.resolve(Mode.ASK, false, true).action);
    }

    @Test
    public void askModeUnavailableWhenNeitherExists() {
        assertEquals(Action.UNAVAILABLE, TargetResolver.resolve(Mode.ASK, false, false).action);
    }

    // ---- Mode parsing (stored preferences) ----

    @Test
    public void fromStringParsesAllKnownValues() {
        assertEquals(Mode.APP, Mode.fromString("app"));
        assertEquals(Mode.WEB, Mode.fromString("web"));
        assertEquals(Mode.ASK, Mode.fromString("ask"));
        assertEquals(Mode.AUTO, Mode.fromString("auto"));
    }

    @Test
    public void fromStringDefaultsToAutoOnGarbageAndNull() {
        assertEquals(Mode.AUTO, Mode.fromString(null));
        assertEquals(Mode.AUTO, Mode.fromString(""));
        assertEquals(Mode.AUTO, Mode.fromString("APP"));   // case-sensitive storage
        assertEquals(Mode.AUTO, Mode.fromString("gibberish"));
    }

    // ---- Constant pins ----

    @Test
    public void settingsDefaultModeParsesToAuto() {
        assertEquals(Mode.AUTO, Mode.fromString(SettingsKeys.DEFAULT_MODE));
    }

    @Test
    public void kimiPackageNameMatchesOfficialApp() {
        // The official Kimi app by Beijing Moonshot AI; changing this string
        // silently breaks the widget's primary path, so it is pinned by test.
        assertEquals("com.moonshot.kimichat", KimiTargets.KIMI_PACKAGE);
    }

    @Test
    public void webUrlIsSecureAndOnKimiDomain() {
        assertTrue(KimiTargets.KIMI_WEB_URL.startsWith("https://"));
        assertTrue(KimiTargets.KIMI_WEB_URL.contains("kimi.com"));
    }
}
