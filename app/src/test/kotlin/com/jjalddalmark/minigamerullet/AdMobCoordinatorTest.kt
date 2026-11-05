package com.jjalddalmark.minigamerullet

import com.jjalddalmark.minigamerullet.ads.AdMobCoordinator
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobCoordinatorTest {

    @Test
    fun officialAdMobValidationAcceptsOnlyOfficialPublisherIds() {
        assertTrue(
            AdMobCoordinator.isValidOfficialAppId("ca-app-pub-5019798166308009~1000000001")
        )
        assertFalse(
            AdMobCoordinator.isValidOfficialAppId("ca-app-pub-1111222233334444~1000000001")
        )
        assertFalse(
            AdMobCoordinator.isValidOfficialAppId("ca-app-pub-5019798166308009/1000000001")
        )

        val validUnit = "ca-app-pub-5019798166308009/1000000002"
        assertTrue(AdMobCoordinator.isValidOfficialUnitId(validUnit))
        assertEquals(validUnit, AdMobCoordinator.ensureOfficialAdUnitId("  $validUnit  "))
        assertEquals("", AdMobCoordinator.ensureOfficialAdUnitId("ca-app-pub-1111222233334444/1000000002"))
        assertEquals("", AdMobCoordinator.ensureOfficialAdUnitId(""))
    }

    @Test
    fun admobPropertiesContainsValidOfficialIdsAndZeroSamplePublisherIds() {
        val candidates = listOf(
            File("../admob.properties"),
            File("admob.properties")
        )
        val propFile = candidates.firstOrNull { it.isFile }
        assertTrue("admob.properties 파일이 프로젝트 루트에 존재해야 합니다.", propFile != null)

        val props = Properties().apply {
            propFile!!.readText(Charsets.UTF_8).removePrefix("\uFEFF").reader().use { load(it) }
        }
        val appId = props.getProperty("ADMOB_APP_ID").orEmpty()
        val bannerHomeId = props.getProperty("ADMOB_BANNER_HOME_UNIT_ID").orEmpty()
        val interstitialExitId = props.getProperty("ADMOB_INTERSTITIAL_EXIT_UNIT_ID").orEmpty()

        assertTrue(AdMobCoordinator.isValidOfficialAppId(appId))
        assertTrue(AdMobCoordinator.isValidOfficialUnitId(bannerHomeId))
        assertTrue(AdMobCoordinator.isValidOfficialUnitId(interstitialExitId))
    }

    @Test
    fun cooldownAndCountdownBehaveAccurately() {
        assertTrue(
            AdMobCoordinator.shouldShowInterstitialByCooldown(
                nowElapsedMs = 0L,
                lastShownElapsedMs = -AdMobCoordinator.INTERSTITIAL_COOLDOWN_MS
            )
        )
        assertFalse(
            AdMobCoordinator.shouldShowInterstitialByCooldown(
                nowElapsedMs = 30_000L,
                lastShownElapsedMs = 0L
            )
        )
        assertTrue(
            AdMobCoordinator.shouldShowInterstitialByCooldown(
                nowElapsedMs = 60_000L,
                lastShownElapsedMs = 0L
            )
        )

        assertEquals(3, AdMobCoordinator.computeCountdownSeconds(2_500L))
        assertEquals(2, AdMobCoordinator.computeCountdownSeconds(1_800L))
        assertEquals(1, AdMobCoordinator.computeCountdownSeconds(600L))
        assertEquals(0, AdMobCoordinator.computeCountdownSeconds(0L))
    }
}
