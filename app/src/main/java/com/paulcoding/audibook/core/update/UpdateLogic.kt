package com.paulcoding.audibook.core.update

/** True when [remote] is a higher dotted version than [current]; a leading "v" and missing parts are ignored. */
fun isNewer(remote: String, current: String): Boolean {
    val r = parts(remote)
    val c = parts(current)
    for (i in 0 until maxOf(r.size, c.size)) {
        val diff = r.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}

private fun parts(version: String) =
    version.trim().removePrefix("v").removePrefix("V").split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

/** Turns GitHub's Markdown release notes into plain text: drops emphasis and headings, bullets list items, unwraps links. */
fun plainReleaseNotes(markdown: String): String = markdown.lines()
    .map { line ->
        line.replace(Regex("\\*\\*|__|`"), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "• ")
            .trimEnd()
    }
    .joinToString("\n")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

/** Picks the release APK for the device's preferred ABI (assets end in "-<abi>.apk" or "-<abi>-release.apk"), else the universal one. */
fun pickApk(assetNames: List<String>, supportedAbis: List<String>): String? {
    val apks = assetNames.filter { it.endsWith(".apk") }
    fun find(tag: String) = apks.firstOrNull { it.endsWith("-$tag.apk") || it.endsWith("-$tag-release.apk") }
    return supportedAbis.firstNotNullOfOrNull(::find) ?: find("universal")
}
