package com.shilapi.xcertplay

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LegacyConnectionProbeTest {

    @Test
    fun vendorBtServicesLineReportsResolvedAnwPhoneLinkAndHsaePackage() {
        val pm = mock(PackageManager::class.java)
        `when`(pm.queryIntentServices(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(mock(ResolveInfo::class.java)))
        `when`(pm.getPackageInfo(eq("com.hsae.bluetoothservice"), anyInt()))
            .thenReturn(PackageInfo())
        val line = LegacyConnectionProbe.vendorBtServicesLine(pm)
        assertTrue("expected resolved count in: $line", line.contains("anwPhoneLinkResolved=1"))
        assertTrue("expected hsae presence in: $line", line.contains("hsaeBluetoothService=true"))
    }

    @Test
    fun vendorBtServicesLineReportsAbsentVendorServices() {
        val pm = mock(PackageManager::class.java)
        `when`(pm.queryIntentServices(any(Intent::class.java), anyInt()))
            .thenReturn(emptyList())
        `when`(pm.getPackageInfo(eq("com.hsae.bluetoothservice"), anyInt()))
            .thenThrow(PackageManager.NameNotFoundException::class.java)
        val line = LegacyConnectionProbe.vendorBtServicesLine(pm)
        assertTrue("expected zero resolution in: $line", line.contains("anwPhoneLinkResolved=0"))
        assertTrue("expected hsae absence in: $line", line.contains("hsaeBluetoothService=false"))
    }

    @Test
    fun vendorBtServicesLineSurvivesPackageManagerFailure() {
        val pm = mock(PackageManager::class.java)
        `when`(pm.queryIntentServices(any(Intent::class.java), anyInt()))
            .thenThrow(RuntimeException("probe failed"))
        val line = LegacyConnectionProbe.vendorBtServicesLine(pm)
        assertTrue("expected -1 sentinel in: $line", line.contains("anwPhoneLinkResolved=-1"))
    }
}
