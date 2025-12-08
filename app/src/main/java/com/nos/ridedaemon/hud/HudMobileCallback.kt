package com.nos.ridedaemon.hud

import api.MobileCallback
import api.MobileEvent

class HudMobileCallback(
    private val onNonFatalErr: (String) -> Unit = {},
    private val onFatalErr: (String) -> Unit = {},
    private val onNewEvent: (MobileEvent) -> Unit = {},
    private val onStreamStopped: () -> Unit = {}
) : MobileCallback {
    override fun onError(err: String?, fatal: Boolean) {
        if (fatal) {
            onFatalErr(err ?: "Unknown fatal error")
        } else {
            onNonFatalErr(err ?: "Unknown non fatal error")
        }
    }

    override fun onEvent(time: Long, type: Long, body: ByteArray?) {
        val evt = MobileEvent()
        evt.time = time
        evt.type = type
        evt.payload = body
        onNewEvent(evt)
    }

    override fun onStopped() {
        onStreamStopped()
    }
}