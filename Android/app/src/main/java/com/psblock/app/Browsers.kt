package com.psblock.app

/**
 * Comprehensive set of Android browser package names that the title monitor watches.
 * Kept here as the single source of truth; the same list is mirrored in
 * res/xml/accessibility_service_config.xml so the OS only dispatches events from these apps
 * (keeps CPU/battery cost low).
 */
object Browsers {
    val PACKAGES = setOf(
        // Chrome & channels
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        // Firefox family
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "org.mozilla.fennec_aurora", "org.mozilla.focus", "org.mozilla.klar",
        "us.spotco.fennec_dos", "io.github.forkmaintainers.iceraven",
        // Edge
        "com.microsoft.emmx", "com.microsoft.emmx.beta", "com.microsoft.emmx.dev",
        "com.microsoft.emmx.canary",
        // Opera
        "com.opera.browser", "com.opera.browser.beta", "com.opera.gx", "com.opera.mini.native",
        // Brave
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        // Vivaldi
        "com.vivaldi.browser", "com.vivaldi.browser.snapshot",
        // Samsung Internet
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        // DuckDuckGo / Kiwi / UC / Via / Tor
        "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.kiwibrowser.browser.dev",
        "com.UCMobile.intl", "com.UCMobile.x86", "mark.via.gp", "mark.via",
        "org.torproject.torbrowser",
        // Yandex / Ecosia / Lightning / Qwant / Puffin / Aloha
        "com.yandex.browser", "com.yandex.browser.beta", "com.ecosia.android",
        "acr.browser.lightning", "com.qwant.liberty",
        "com.cloudmosa.puffinFree", "com.cloudmosa.puffin", "com.aloha.browser",
        // Adblock / privacy browsers
        "org.adblockplus.browser", "com.stoutner.privacybrowser.standard",
        "org.lineageos.jelly", "org.bromite.bromite",
        // OEM browsers
        "com.mi.globalbrowser", "com.mi.globalbrowser.mini", "com.huawei.browser",
        "com.heytap.browser", "com.coloros.browser", "com.vivo.browser",
        "com.oneplus.browser", "com.android.browser", "com.jio.web",
        // Others
        "com.naver.whale", "com.mx.browser", "com.mx.browser.tablet",
        "com.transsion.phoenix", "mobi.mgeek.TunnyBrowser", "com.boatbrowser.free",
        "com.ksmobile.cb", "net.onecook.browser", "com.cake.browser",
        "phx.hot.browser", "com.fulldive.mobile"
    )
}
