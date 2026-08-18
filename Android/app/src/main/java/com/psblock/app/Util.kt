package com.psblock.app

import android.content.Context

object Util {
    /** Human-friendly remaining time, localised (mirrors fmtTime in main.js but bilingual). */
    fun fmtTime(ctx: Context, sec: Int): String {
        if (sec < 0) return "∞"
        val s = sec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        val hU = ctx.getString(R.string.unit_hour)
        val mU = ctx.getString(R.string.unit_min_short)
        val sU = ctx.getString(R.string.unit_sec_short)
        return if (h > 0) "$h$hU $m$mU" else "$m$mU ${ss.toString().padStart(2, '0')}$sU"
    }
}
