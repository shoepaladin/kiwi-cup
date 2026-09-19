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
    fun `android 16 and 17 are still covered`() {
        // The threshold is open-ended on purpose: each release has tightened sideload handling,
        // never loosened it, so a future API must not fall through to "unrestricted".
        listOf(36, 37, 38).forEach { sdk ->
            assertTrue("API $sdk should restrict sms", InstallSource.smsLikelyRestricted(sdk, FILE_MANAGER))
        }
    }

    @Test
    fun `the installer verdict says nothing about the role`() {
        // A Pixel 8a on API 37, installed by com.google.android.packageinstaller, reported the
        // role available and offerable while every SMS permission sat denied. Anything claiming
        // the installer blocks the role would have to contradict that device, so nothing here
        // may grow a roleAlsoRestricted-shaped function again.
        val sideloaded = InstallSource.smsLikelyRestricted(37, "com.google.android.packageinstaller")
        assertTrue("the permission group is genuinely restricted", sideloaded)
        assertEquals(
            "the role is offerable regardless of the installer verdict",
            PermissionGateState.REQUEST_ROLE,
            gateState(
                listOf(neverAsked(READ_SMS), neverAsked(RECEIVE_SMS), neverAsked(SEND_SMS)),
                setOf(READ_SMS, RECEIVE_SMS, SEND_SMS),
                role = SmsRoleStatus.OFFERABLE,
                roleOffered = false,
                restrictedByInstaller = sideloaded
            )
        )
    }
}

class RestrictedByInstallerGateTest {

    private val smsRequired = setOf(READ_SMS, RECEIVE_SMS, SEND_SMS)
    private val freshInstall = listOf(neverAsked(READ_SMS), neverAsked(RECEIVE_SMS), neverAsked(SEND_SMS))

    @Test
    fun `a sideloaded first launch offers the role rather than giving up`() {
        // The regression this exists for, reproduced from a real device report: the gate saw a
        // non-allowlisting installer and returned RESTRICTED before asking for anything, so the
        // report came back with asks=0 on every permission and a role that was offerable all
        // along. The app refused to try and then showed the user instructions blaming Android.
        assertEquals(
            PermissionGateState.REQUEST_ROLE,
            gateState(freshInstall, smsRequired, role = SmsRoleStatus.OFFERABLE, restrictedByInstaller = true)
        )
    }

    @Test
    fun `the role is offered before any permission request on an allowlisted install too`() {
        // Not a sideload special case: the role is required to send and receive either way, and
        // requesting the SMS group without it is the call that gets refused.
        assertEquals(
            PermissionGateState.REQUEST_ROLE,
            gateState(freshInstall, smsRequired, role = SmsRoleStatus.OFFERABLE, restrictedByInstaller = false)
        )
    }

    @Test
    fun `instructions appear only once the role has been offered and not taken`() {
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(
                freshInstall,
                smsRequired,
                role = SmsRoleStatus.OFFERABLE,
                roleOffered = true,
                restrictedByInstaller = true
            )
        )
    }

    @Test
    fun `holding the role routes through the ordinary prompt even on a sideload`() {
        // With the role held the group is grantable by the ordinary route, so the sideload
        // instructions would be telling the user to fix something that is no longer broken.
        assertEquals(
            PermissionGateState.ASK,
            gateState(
                freshInstall,
                smsRequired,
                role = SmsRoleStatus.HELD,
                roleOffered = true,
                restrictedByInstaller = true
            )
        )
    }

    @Test
    fun `a device with no sms role falls straight through to the instructions`() {
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(freshInstall, smsRequired, role = SmsRoleStatus.UNAVAILABLE, restrictedByInstaller = true)
        )
    }

    @Test
    fun `the role is not offered twice`() {
        // Re-offering a role the user just declined is a loop, not a retry.
        assertEquals(
            PermissionGateState.ASK,
            gateState(
                freshInstall,
                smsRequired,
                role = SmsRoleStatus.OFFERABLE,
                roleOffered = true,
                restrictedByInstaller = false
            )
        )
    }

    @Test
    fun `granted permissions open the gate before the role is even considered`() {
        val states = listOf(granted(READ_SMS, 1), granted(RECEIVE_SMS, 1), granted(SEND_SMS, 1))
        assertEquals(
            PermissionGateState.READY,
            gateState(states, smsRequired, role = SmsRoleStatus.OFFERABLE, restrictedByInstaller = true)
        )
    }

    @Test
    fun `a partial grant on a sideloaded install still shows instructions once the role is spent`() {
        val states = listOf(granted(READ_SMS, 1), granted(RECEIVE_SMS, 1), autoDenied(SEND_SMS))
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(states, smsRequired, role = SmsRoleStatus.OFFERABLE, roleOffered = true, restrictedByInstaller = true)
        )
    }

    @Test
    fun `the installer flag outranks a retryable denial once the role is spent`() {
        val states = listOf(deniedOnce(READ_SMS), granted(RECEIVE_SMS), granted(SEND_SMS))
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(states, smsRequired, role = SmsRoleStatus.OFFERABLE, roleOffered = true, restrictedByInstaller = true)
        )
    }
}

class RoleAttemptTest {

    @Test
    fun `an unlaunched attempt says nothing`() {
        assertEquals(RestrictedCause.UNKNOWN, classify(RoleAttempt()))
    }

    @Test
    fun `a result faster than a person could produce is the system refusing`() {
        // The device case: the dialog never drew, so the result came straight back. Tens of
        // milliseconds is not a decision, it is a rejection.
        val attempt = RoleAttempt(launched = true, accepted = false, elapsedMs = 40)
        assertEquals(RestrictedCause.ROLE_REFUSED_BY_SYSTEM, classify(attempt))
    }

    @Test
    fun `a considered no is read as the user declining`() {
        val attempt = RoleAttempt(launched = true, accepted = false, elapsedMs = 4_000)
        assertEquals(RestrictedCause.ROLE_DECLINED_BY_USER, classify(attempt))
    }

    @Test
    fun `the boundary belongs to the user rather than the system`() {
        // Exactly at the floor is attributed to a person: calling a real decline a system refusal
        // would send the user off to change a setting that was never the problem.
        val atFloor = RoleAttempt(launched = true, elapsedMs = HUMAN_REACTION_FLOOR_MS)
        assertEquals(RestrictedCause.ROLE_DECLINED_BY_USER, classify(atFloor))

        val justUnder = RoleAttempt(launched = true, elapsedMs = HUMAN_REACTION_FLOOR_MS - 1)
        assertEquals(RestrictedCause.ROLE_REFUSED_BY_SYSTEM, classify(justUnder))
    }

    @Test
    fun `a launch that threw means no dialog exists to draw`() {
        val attempt = RoleAttempt(launched = true, failedToLaunch = true, elapsedMs = 5)
        assertEquals(RestrictedCause.ROLE_UNAVAILABLE, classify(attempt))
    }

    @Test
    fun `an accepted role is not a restriction to explain`() {
        // Accepting fast is still accepting; the timing rule must not apply to a success.
        val attempt = RoleAttempt(launched = true, accepted = true, elapsedMs = 10)
        assertEquals(RestrictedCause.UNKNOWN, classify(attempt))
    }
}

class RestrictedPermissionHelpTest {

    @Test
    fun `the instructions name the overflow menu item that actually unblocks it`() {
        // Without this exact phrase the instructions send the user in a circle, because the SMS
        // toggle in settings keeps refusing until the overflow item is tapped first.
        assertTrue(RestrictedPermissionHelp.steps().any { it.contains(RestrictedPermissionHelp.OVERFLOW_ITEM) })
    }

    @Test
    fun `the steps are ordered app info first then permissions`() {
        RestrictedCause.entries.forEach { cause ->
            val steps = RestrictedPermissionHelp.steps(cause)
            val appInfo = steps.indexOfFirst { it.contains("App info") }
            val permissions = steps.indexOfFirst { it.contains("open Permissions") }
            assertTrue("$cause names both", appInfo >= 0 && permissions >= 0)
            assertTrue("$cause: App info must come before the Permissions step", appInfo < permissions)
        }
    }

    private fun roleIndex(cause: RestrictedCause) =
        RestrictedPermissionHelp.steps(cause).indexOfFirst { it.contains(RestrictedPermissionHelp.ROLE_ACTION) }

    private fun overflowIndex(cause: RestrictedCause) =
        RestrictedPermissionHelp.steps(cause).indexOfFirst { it.contains(RestrictedPermissionHelp.OVERFLOW_ITEM) }

    @Test
    fun `by default the role is offered before the settings detour`() {
        // Sending the user through App info first is wasted taps whenever the dialog would have
        // worked, so the role leads unless we have evidence the system refused it.
        assertTrue(roleIndex(RestrictedCause.UNKNOWN) < overflowIndex(RestrictedCause.UNKNOWN))
    }

    @Test
    fun `a user who declined the role is offered it again before the detour`() {
        assertTrue(
            roleIndex(RestrictedCause.ROLE_DECLINED_BY_USER) <
                overflowIndex(RestrictedCause.ROLE_DECLINED_BY_USER)
        )
    }

    @Test
    fun `a system refusal puts the restriction first because the role cannot work yet`() {
        // The case a Pixel 8a on API 37 produced: role available, intent created, no dialog drawn.
        // Offering the role again first would just repeat a refusal the user cannot act on.
        val cause = RestrictedCause.ROLE_REFUSED_BY_SYSTEM
        assertTrue("the restriction must be lifted first", overflowIndex(cause) < roleIndex(cause))
    }

    @Test
    fun `every ordering still names the overflow item and the role action`() {
        RestrictedCause.entries.forEach { cause ->
            assertTrue("$cause must name the overflow item", overflowIndex(cause) >= 0)
            assertTrue("$cause must name the role action", roleIndex(cause) >= 0)
        }
    }

    @Test
    fun `there is an adb alternative for anyone with a computer`() {
        assertTrue(RestrictedPermissionHelp.ADB_ALTERNATIVE.contains("adb install"))
    }
}
