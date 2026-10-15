package com.dolanan.usbscreenmirror.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbEndpoint

data class AdbUsbInterface(val intf: UsbInterface, val bulkIn: UsbEndpoint, val bulkOut: UsbEndpoint)

object UsbAdbDevice {
    /** ADB interface: class 0xff, subclass 0x42, protocol 0x01, with bulk IN and OUT. */
    fun find(device: UsbDevice): AdbUsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass != 0xff || intf.interfaceSubclass != 0x42 || intf.interfaceProtocol != 0x01) continue
            var input: UsbEndpoint? = null
            var output: UsbEndpoint? = null
            for (e in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(e)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (endpoint.direction == UsbConstants.USB_DIR_IN) input = endpoint
                if (endpoint.direction == UsbConstants.USB_DIR_OUT) output = endpoint
            }
            if (input != null && output != null) return AdbUsbInterface(intf, input, output)
        }
        return null
    }
}
