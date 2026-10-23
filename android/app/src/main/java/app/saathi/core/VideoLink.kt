package app.saathi.core

fun videoLink(url: String?): String? {
    if (url == null) return null
    val trimmed = url.trim()
    return if (trimmed.startsWith("https://")) trimmed else null
}
