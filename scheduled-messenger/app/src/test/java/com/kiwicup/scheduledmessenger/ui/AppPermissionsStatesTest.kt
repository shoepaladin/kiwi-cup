package com.kiwicup.scheduledmessenger.ui

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.PermissionGateState
import com.kiwicup.scheduledmessenger.core.RestrictedPermissions
import com.kiwicup.scheduledmessenger.core.SmsRoleStatus
import com.kiwicup.scheduledmessenger.core.gateState
import com.kiwicup.scheduledmessenger.ui.permissions.AppPermissions
import com.kiwicup.scheduledmessenger.ui.permissions.PermissionAskLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

/** The real permission list wired to the real diagnosis, rather than hand-built states. */
@RunWith(AndroidJUnit4::class)
class AppPermissionsStatesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var log: PermissionAskLog

    @Before
    fun setUp() {
        log = PermissionAskLog(context)
        log.reset()
    }

    private fun grant(vararg permissions: String) {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(*permissions)
    }

    @Test
    fun everyPermissionTheAppRequiresIsOneAndroidRestricts() {
        // If this ever fails, the gate is requiring something the restricted diagnosis cannot
        // reason about, and the sideload screen would stop appearing for it.
        AppPermissions.required.forEach {
            assertTrue("$it should be a restricted permission", RestrictedPermissions.isRestricted(it))
        }
    }

    @Test
    fun theBatchCarriesOptionalPermissionsSoARestrictionCanBeCorroborated() {
        val optional = AppPermissions.all.filterNot { RestrictedPermissions.isRestricted(it) }
        assertTrue("the batch needs at least one unrestricted permission", optional.isNotEmpty())
    }

    @Test
    fun aFreshInstallHasAskedForNothing() {
        val states = AppPermissions.states(context, activity = null, log = log)
        assertTrue(states.all { it.timesAsked == 0 })
        assertEquals(PermissionGateState.ASK, gateState(states, AppPermissions.requiredSet))
    }

    @Test
    fun theSideloadCaseIsDiagnosedAsRestricted() {
        // Exactly what a sideloaded install does: we ask once, and the SMS group comes back
        // denied with no dialog and no rationale, while nothing else has changed.
        log.recordAsked(AppPermissions.all)
        val states = AppPermissions.states(context, activity = null, log = log)

        assertEquals(PermissionGateState.RESTRICTED, gateState(states, AppPermissions.requiredSet))
    }

    @Test
    fun grantingTheSmsGroupOpensTheGate() {
        log.recordAsked(AppPermissions.all)
        grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)

        val states = AppPermissions.states(context, activity = null, log = log)
        assertEquals(PermissionGateState.READY, gateState(states, AppPermissions.requiredSet))
    }

    @Test
    fun missingOptionalPermissionsDoNotHoldTheGateShut() {
        log.recordAsked(AppPermissions.all)
        grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)

        val states = AppPermissions.states(context, activity = null, log = log)
        // Contacts and friends are still denied here.
        assertTrue(states.any { !it.granted })
        assertEquals(PermissionGateState.READY, gateState(states, AppPermissions.requiredSet))
    }

    @Test
    fun theInstallerCheckRunsWithoutThrowing() {
        // getInstallSourceInfo can throw on some devices; a failure there must degrade to
        // "treat as sideloaded" rather than crashing the gate before anything is on screen.
        AppPermissions.smsRestrictedByInstaller(context)
    }

    @Test
    fun aSideloadedFirstLaunchOffersTheRoleInsteadOfGivingUp() {
        // The regression, straight from a device report: the gate read a non-Play-Store installer
        // and returned RESTRICTED before asking for anything, so every permission came back
        // asks=0 while the role sat offerable and unasked.
        val states = AppPermissions.states(context, activity = null, log = log)
        assertEquals(
            PermissionGateState.REQUEST_ROLE,
            gateState(
                states,
                AppPermissions.requiredSet,
                role = SmsRoleStatus.OFFERABLE,
                roleOffered = false,
                restrictedByInstaller = true
            )
        )
    }

    @Test
    fun instructionsAppearOnlyAfterTheRoleWasOfferedAndNotTaken() {
        log.recordAsked(AppPermissions.all)
        val states = AppPermissions.states(context, activity = null, log = log)
        assertEquals(
            PermissionGateState.RESTRICTED,
            gateState(
                states,
                AppPermissions.requiredSet,
                role = SmsRoleStatus.OFFERABLE,
                roleOffered = true,
                restrictedByInstaller = true
            )
        )
    }

    @Test
    fun aSideloadedInstallStillOpensOnceTheUserClearsTheRestriction() {
        grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        val states = AppPermissions.states(context, activity = null, log = log)
        assertEquals(
            PermissionGateState.READY,
            gateState(
                states,
                AppPermissions.requiredSet,
                role = SmsRoleStatus.OFFERABLE,
                restrictedByInstaller = true
            )
        )
    }

    @Test
    fun statesReportTheAskCountBackFromTheLog() {
        log.recordAsked(AppPermissions.all)
        log.recordAsked(AppPermissions.all)
        val states = AppPermissions.states(context, activity = null, log = log)
        assertTrue(states.all { it.timesAsked == 2 })
    }
}
