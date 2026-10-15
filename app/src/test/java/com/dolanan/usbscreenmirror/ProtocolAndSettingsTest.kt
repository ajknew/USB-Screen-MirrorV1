package com.dolanan.usbscreenmirror

import com.dolanan.usbscreenmirror.adb.AdbPacket
import com.dolanan.usbscreenmirror.adb.AdbPacketParser
import com.dolanan.usbscreenmirror.model.Preset
import com.dolanan.usbscreenmirror.model.Resolution
import com.dolanan.usbscreenmirror.model.VideoSettings
import org.junit.Assert.*
import org.junit.Test

class ProtocolAndSettingsTest {
    @Test fun adbPacketRoundTripsWhenReadIsFragmented() {
        val expected = AdbPacket(AdbPacket.WRTE, 3, 7, byteArrayOf(0, 1, 2, 3, 4, 5))
        val encoded = expected.encode()
        val parser = AdbPacketParser()
        assertTrue(parser.feed(encoded.copyOfRange(0, 11)).isEmpty())
        val parsed = parser.feed(encoded.copyOfRange(11, encoded.size))
        assertEquals(1, parsed.size)
        assertEquals(expected.command, parsed[0].command)
        assertEquals(expected.arg0, parsed[0].arg0)
        assertEquals(expected.arg1, parsed[0].arg1)
        assertArrayEquals(expected.payload, parsed[0].payload)
    }

    @Test fun presetsUseCorrectBitrateUnits() {
        val save = VideoSettings.forPreset(Preset.SAVE)
        assertEquals(Resolution.P480, save.resolution)
        assertEquals(15, save.fps)
        assertEquals(500_000, save.bitrateBitsPerSecond())
        val balanced = VideoSettings.forPreset(Preset.BALANCED)
        assertEquals(1_500_000, balanced.bitrateBitsPerSecond())
        val high = VideoSettings.forPreset(Preset.HIGH)
        assertEquals(4_000_000, high.bitrateBitsPerSecond())
    }

    @Test fun settingsClampToSupportedUiRange() {
        val validated = VideoSettings(Resolution.P720, 90, 20f, Preset.CUSTOM).validate()
        assertEquals(30, validated.fps)
        assertEquals(8f, validated.bitrateMbps)
    }
}
