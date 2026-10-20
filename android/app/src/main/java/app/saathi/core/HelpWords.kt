package app.saathi.core

/** English, Hindi and Hinglish words for asking for help. Same list as the server, so offline she is still heard. */
private val HELP_WORDS = Regex(
    """(\b(help|scared|afraid|follow(ing|ed)?|touch(ed|ing)?|unsafe|police|madad|bachao|darr?|peecha)\b|पीछा|मदद|बचाओ)""",
    RegexOption.IGNORE_CASE,
)

fun asksForHelp(text: String) = HELP_WORDS.containsMatchIn(text)
