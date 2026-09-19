package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val READ_SMS = "android.permission.READ_SMS"
private const val RECEIVE_SMS = "android.permission.RECEIVE_SMS"
private const val SEND_SMS = "android.permission.SEND_SMS"
private const val READ_CONTACTS = "android.permission.READ_CONTACTS"
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

private fun granted(permission: String, timesAsked: Int = 1) =
    PermissionState(permission, granted = true, canShowRationale = false, timesAsked = timesAsked)

private fun neverAsked(permission: String) =
    PermissionState(permission, granted = false, canShowRationale = false, timesAsked = 0)

/** A user tapping "Don't allow" once: Android still offers a rationale. */
private fun deniedOnce(permission: String) =
    PermissionState(permission, granted = false, canShowRationale = true, timesAsked = 1)

/** A user who denied twice: Android stops prompting and withdraws the rationale. */
private fun deniedForGood(permission: String, timesAsked: Int = 2) =
    PermissionState(permission, granted = false, canShowRationale = false, timesAsked = timesAsked)

/** The sideload signature: denied with no rationale, though we only ever asked once. */
private fun autoDenied(permission: String, timesAsked: Int = 1) =
    PermissionState(permission, granted = false, canShowRationale = false, timesAsked = timesAsked)

class RestrictedPermissionsTest {

    @Test
    fun `the whole sms group is restricted`() {
        listOf(READ_SMS, RECEIVE_SMS, SEND_SMS, "android.permission.RECEIVE_MMS").forEach {
            assertTrue("$it should be restricted", RestrictedPermissions.isRestricted(it))
        }
    }

    @Test
    fun `call log permissions are restricted too`() {
        assertTrue(RestrictedPermissions.isRestricted("android.permission.READ_CALL_LOG"))
        assertTrue(RestrictedPermissions.isRestricted("android.permission.WRITE_CALL_LOG"))
    }

    @Test
    fun `ordinary permissions are not restricted`() {
        listOf(READ_CONTACTS, POST_NOTIFICATIONS, "android.permission.READ_PHONE_STATE", "").forEach {
            assertFalse("$it should not be restricted", RestrictedPermissions.isRestricted(it))
        }
    }

    @Test
    fun `restricted set is the union of sms and call log with no overlap`() {
        assertEquals(RestrictedPermissions.sms.size + RestrictedPermissions.callLog.size, RestrictedPermissions.all.size)
    }

    @Test
    fun `permission state reports its own restriction`() {
        assertTrue(autoDenied(SEND_SMS).isRestricted)
        assertFalse(deniedOnce(READ_CONTACTS).isRestricted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative ask count is rejected`() {
        PermissionState(SEND_SMS, granted = false, canShowRationale = false, timesAsked = -1)
    }
}

class PermissionDiagnosticsTest {

    @Test
    fun `granted beats everything else`() {
        assertEquals(PermissionDiagnosis.GRANTED, PermissionDiagnostics.diagnose(granted(SEND_SMS)))
    }

    @Test
    fun `a permission we never asked for is simply unasked`() {
        assertEquals(PermissionDiagnosis.NOT_YET_ASKED, PermissionDiagnostics.diagnose(neverAsked(SEND_SMS)))
    }

    @Test
    fun `never asked is not mistaken for restricted even though rationale is false`() {
        // Both cases have canShowRationale = false; only the ask count separates them.
        assertEquals(PermissionDiagnosis.NOT_YET_ASKED, PermissionDiagnostics.diagnose(neverAsked(READ_SMS)))
        assertEquals(PermissionDiagnosis.BLOCKED_AS_RESTRICTED, PermissionDiagnostics.diagnose(autoDenied(READ_SMS)))
    }

    @Test
    fun `one user denial can be retried`() {
        assertEquals(PermissionDiagnosis.DENIED_CAN_RETRY, PermissionDiagnostics.diagnose(deniedOnce(SEND_SMS)))
        assertEquals(PermissionDiagnosis.DENIED_CAN_RETRY, PermissionDiagnostics.diagnose(deniedOnce(READ_CONTACTS)))
    }

    @Test
    fun `a restricted permission denied on the very first ask is diagnosed as restricted`() {
        assertEquals(PermissionDiagnosis.BLOCKED_AS_RESTRICTED, PermissionDiagnostics.diagnose(autoDenied(SEND_SMS)))
    }

    @Test
    fun `an ordinary permission denied without rationale is a settings problem not a restriction`() {
        assertEquals(
            PermissionDiagnosis.DENIED_NEEDS_SETTINGS,
            PermissionDiagnostics.diagnose(deniedForGood(READ_CONTACTS))
        )
    }

    @Test
    fun `a restricted permission stays diagnosed as restricted across repeated asks`() {
        // The user keeps tapping "Grant permissions"; the count climbs but nothing changes.
        val states = listOf(
            autoDenied(READ_SMS, timesAsked = 4),
            autoDenied(SEND_SMS, timesAsked = 4),
            granted(READ_CONTACTS, timesAsked = 4)
        )
        val result = PermissionDiagnostics.diagnose(states)
        assertEquals(PermissionDiagnosis.BLOCKED_AS_RESTRICTED, result[READ_SMS])
        assertEquals(PermissionDiagnosis.BLOCKED_AS_RESTRICTED, result[SEND_SMS])
    }

    @Test
    fun `a sibling that still offers a rationale also proves the dialog is being drawn`() {
        val states = listOf(
            autoDenied(SEND_SMS, timesAsked = 3),
            deniedOnce(READ_CONTACTS).copy(timesAsked = 3)
        )
        assertEquals(PermissionDiagnosis.BLOCKED_AS_RESTRICTED, PermissionDiagnostics.diagnose(states)[SEND_SMS])
    }

    @Test
    fun `without a sibling to corroborate a repeated refusal reads as a permanent denial`() {
        // Honest limitation: with nothing else in the batch we cannot tell the two apart after
        // the first ask, and pointing at settings is the safer of the two wrong answers.
        val states = listOf(autoDenied(SEND_SMS, timesAsked = 3))
        assertEquals(PermissionDiagnosis.DENIED_NEEDS_SETTINGS, PermissionDiagnostics.diagnose(states)[SEND_SMS])
    }

    @Test
    fun `a sibling denied for good does not count as evidence of a dialog`() {
        val states = listOf(
            autoDenied(SEND_SMS, timesAsked = 3),
            deniedForGood(READ_CONTACTS, timesAsked = 3)
        )
        assertEquals(PermissionDiagnosis.DENIED_NEEDS_SETTINGS, PermissionDiagnostics.diagnose(states)[SEND_SMS])
    }

    @Test
    fun `an unasked sibling is not evidence of a dialog either`() {
        val states = listOf(
            autoDenied(SEND_SMS, timesAsked = 2),
            neverAsked(READ_CONTACTS)
        )
        assertEquals(PermissionDiagnosis.DENIED_NEEDS_SETTINGS, PermissionDiagnostics.diagnose(states)[SEND_SMS])
    }

    @Test
    fun `diagnosing a batch returns one verdict per permission`() {
        val states = listOf(granted(READ_SMS), deniedOnce(SEND_SMS), neverAsked(READ_CONTACTS))
        val result = PermissionDiagnostics.diagnose(states)
        assertEquals(3, result.size)
        assertEquals(PermissionDiagnosis.GRANTED, result[READ_SMS])
        assertEquals(PermissionDiagnosis.DENIED_CAN_RETRY, result[SEND_SMS])
        assertEquals(PermissionDiagnosis.NOT_YET_ASKED, result[READ_CONTACTS])
    }

    @Test
    fun `an empty batch diagnoses nothing`() {
        assertTrue(PermissionDiagnostics.diagnose(emptyList()).isEmpty())
    }
}

class GateStateTest {

    @Test
    fun `everything granted is ready`() {
        assertEquals(
            PermissionGateState.READY,
            gateState(listOf(granted(READ_SMS), granted(SEND_SMS), granted(RECEIVE_SMS)))
        )
    }

    @Test
    fun `no required permissions at all is ready`() {
        assertEquals(PermissionGateState.READY, gateState(emptyList()))
    }

    @Test
    fun `a fresh install asks`() {
        assertEquals(
            PermissionGateState.ASK,
            gateState(listOf(neverAsked(READ_SMS), neverAsked(SEND_SMS)))
        )
    }

    @Test
    fun `a single denial explains and asks again`() {
        assertEquals(
            PermissionGateState.EXPLAIN_AND_ASK,
            gateState(listOf(granted(READ_SMS), deniedOnce(SEND_SMS)))
        )
    }

    @Test
    fun `a permanent denial sends the user to settings`() {
        assertEquals(
            PermissionGateState.OPEN_SETTINGS,
            gateState(listOf(granted(READ_SMS), deniedForGood(SEND_SMS)))
        )
    }

    @Test
    fun `the sideload case shows the restricted screen`() {
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(listOf(autoDenied(READ_SMS), autoDenied(RECEIVE_SMS), autoDenied(SEND_SMS)))
        )
    }

    @Test
    fun `restricted outranks a retryable denial`() {
        // Sending the user round the request loop cannot help while the restriction stands.
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(listOf(autoDenied(SEND_SMS), deniedOnce(READ_SMS)))
        )
    }

    @Test
    fun `restricted outranks an unasked permission`() {
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(listOf(autoDenied(SEND_SMS), neverAsked(READ_SMS)))
        )
    }

    @Test
    fun `restricted outranks the settings advice`() {
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(listOf(autoDenied(SEND_SMS), deniedForGood(READ_SMS)))
        )
    }

    @Test
    fun `an optional permission cannot by itself block the gate`() {
        // Contacts is denied for good, but the app works without it, so the gate opens.
        val states = listOf(granted(READ_SMS), granted(RECEIVE_SMS), granted(SEND_SMS), deniedForGood(READ_CONTACTS))
        assertEquals(PermissionGateState.READY, gateState(states, setOf(READ_SMS, RECEIVE_SMS, SEND_SMS)))
    }

    @Test
    fun `an optional permission still corroborates a restriction on a required one`() {
        // This is why the batch is passed whole: contacts being granted proves the dialog runs,
        // which is what identifies the repeatedly-refused SMS permission as restricted rather
        // than merely denied.
        val states = listOf(
            autoDenied(SEND_SMS, timesAsked = 5),
            granted(READ_CONTACTS, timesAsked = 5)
        )
        assertEquals(PermissionGateState.RESTRICTED, gateState(states, setOf(SEND_SMS)))
    }

    @Test
    fun `a required set naming nothing present is ready`() {
        assertEquals(PermissionGateState.READY, gateState(listOf(deniedForGood(READ_CONTACTS)), setOf(SEND_SMS)))
    }

    @Test
    fun `granting the restricted permissions clears the restricted screen`() {
        // What the user should see the moment "Allow restricted settings" has done its job.
        assertEquals(
            PermissionGateState.READY,
            gateState(listOf(granted(READ_SMS, 2), granted(RECEIVE_SMS, 2), granted(SEND_SMS, 2)))
        )
    }
}

class InstallSourceTest {

    private val FILE_MANAGER = "com.google.android.packageinstaller"

    @Test
    fun `the play store allowlists`() {
        assertTrue(InstallSource.allowlistsRestrictedPermissions(InstallSource.PLAY_STORE))
    }

    @Test
    fun `a null installer means the shell installed it and allowlisted`() {
        // adb install records no installing package and allowlists restricted permissions.
        assertTrue(InstallSource.allowlistsRestrictedPermissions(null))
    }

    @Test
    fun `ordinary sideload installers cannot allowlist`() {
        listOf(FILE_MANAGER, "org.fdroid.fdroid", "com.android.chrome", "dev.imranr.obtainium").forEach {
            assertFalse("$it cannot allowlist", InstallSource.allowlistsRestrictedPermissions(it))
        }
    }

    @Test
    fun `sms is restricted on a sideloaded modern android`() {
        assertTrue(InstallSource.smsLikelyRestricted(sdkInt = 35, installerPackage = FILE_MANAGER))
        assertTrue(InstallSource.smsLikelyRestricted(sdkInt = 29, installerPackage = FILE_MANAGER))
    }

    @Test
    fun `sms is not restricted before android 10`() {
        assertFalse(InstallSource.smsLikelyRestricted(sdkInt = 28, installerPackage = FILE_MANAGER))
    }

    @Test
    fun `sms is not restricted when the installer allowlisted`() {
        assertFalse(InstallSource.smsLikelyRestricted(sdkInt = 35, installerPackage = InstallSource.PLAY_STORE))
        assertFalse(InstallSource.smsLikelyRestricted(sdkInt = 35, installerPackage = null))
    }

    @Test
    fun `the default sms role is only blocked from android 15`() {
        // This is what made the role prompt never appear: on 35+ the role request is refused
        // silently, so leading with it produces no dialog and no progress.
        assertTrue(InstallSource.roleAlsoRestricted(sdkInt = 35, installerPackage = FILE_MANAGER))
        assertFalse(InstallSource.roleAlsoRestricted(sdkInt = 34, installerPackage = FILE_MANAGER))
    }

    @Test
    fun `the role is never blocked when the installer allowlisted`() {
        assertFalse(InstallSource.roleAlsoRestricted(sdkInt = 36, installerPackage = InstallSource.PLAY_STORE))
        assertFalse(InstallSource.roleAlsoRestricted(sdkInt = 36, installerPackage = null))
    }
}

class RestrictedByInstallerGateTest {

    private val smsRequired = setOf(READ_SMS, RECEIVE_SMS, SEND_SMS)

    @Test
    fun `a sideloaded first launch shows instructions without asking first`() {
        // The regression this exists for: on Android 15 the app fired a request that could only
        // ever come back refused, so the user's first experience was a system warning dialog.
        val states = listOf(neverAsked(READ_SMS), neverAsked(RECEIVE_SMS), neverAsked(SEND_SMS))
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(states, smsRequired, restrictedByInstaller = true)
        )
    }

    @Test
    fun `the same first launch asks normally when the installer allowlisted`() {
        val states = listOf(neverAsked(READ_SMS), neverAsked(RECEIVE_SMS), neverAsked(SEND_SMS))
        assertEquals(
            PermissionGateState.ASK,
            gateState(states, smsRequired, restrictedByInstaller = false)
        )
    }

    @Test
    fun `granted permissions still open the gate even on a sideloaded install`() {
        // Once the user clears the restriction the flag stays true, so it must not trap them.
        val states = listOf(granted(READ_SMS, 1), granted(RECEIVE_SMS, 1), granted(SEND_SMS, 1))
        assertEquals(
            PermissionGateState.READY,
            gateState(states, smsRequired, restrictedByInstaller = true)
        )
    }

    @Test
    fun `a partial grant on a sideloaded install still shows instructions`() {
        val states = listOf(granted(READ_SMS, 1), granted(RECEIVE_SMS, 1), autoDenied(SEND_SMS))
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(states, smsRequired, restrictedByInstaller = true)
        )
    }

    @Test
    fun `the installer flag outranks a retryable denial`() {
        val states = listOf(deniedOnce(READ_SMS), granted(RECEIVE_SMS), granted(SEND_SMS))
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(states, smsRequired, restrictedByInstaller = true)
        )
    }
}

class RestrictedPermissionHelpTest {

    @Test
    fun `the instructions name the overflow menu item that actually unblocks it`() {
        // Without this exact phrase the instructions send the user in a circle, because the SMS
        // toggle in settings keeps refusing until the overflow item is tapped first.
        assertTrue(RestrictedPermissionHelp.steps.any { it.contains(RestrictedPermissionHelp.OVERFLOW_ITEM) })
    }

    @Test
    fun `the steps are ordered app info first then permissions`() {
        val appInfo = RestrictedPermissionHelp.steps.indexOfFirst { it.contains("App info") }
        val permissions = RestrictedPermissionHelp.steps.indexOfFirst { it.contains("Permissions") }
        assertTrue(appInfo >= 0 && permissions >= 0)
        assertTrue("App info must come before the Permissions step", appInfo < permissions)
    }

    @Test
    fun `the overflow step comes before the default sms app step`() {
        // On Android 15+ the role is refused until restricted settings are allowed, so
        // instructions that mention becoming the default app first would repeat the bug.
        val overflow = RestrictedPermissionHelp.steps.indexOfFirst {
            it.contains(RestrictedPermissionHelp.OVERFLOW_ITEM)
        }
        val defaultApp = RestrictedPermissionHelp.steps.indexOfFirst { it.contains("default SMS") }
        assertTrue(overflow >= 0 && defaultApp >= 0)
        assertTrue("restricted settings must be allowed before the role is offered", overflow < defaultApp)
    }

    @Test
    fun `there is an adb alternative for anyone with a computer`() {
        assertTrue(RestrictedPermissionHelp.ADB_ALTERNATIVE.contains("adb install"))
    }
}
