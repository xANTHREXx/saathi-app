package app.saathi.core

enum class ClipMode {
    PLAY,
    POSTER
}

fun clipMode(animatorScale: Float): ClipMode {
    return if (animatorScale == 0f) ClipMode.POSTER else ClipMode.PLAY
}
