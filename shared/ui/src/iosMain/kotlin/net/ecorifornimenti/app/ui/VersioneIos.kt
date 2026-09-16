package net.ecorifornimenti.app.ui

import platform.Foundation.NSBundle

/** Su iOS la versione sta nell'Info.plist del bundle. */
actual fun versioneApp(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val nome = info?.get("CFBundleShortVersionString") as? String ?: return ""
    val build = info["CFBundleVersion"] as? String
    return if (build.isNullOrBlank()) nome else "$nome ($build)"
}
