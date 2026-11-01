package com.sameer.yaad.capture

import android.content.Context

object PcmSources {
    fun open(context: Context): PcmSource = MicPcmSource()
}
