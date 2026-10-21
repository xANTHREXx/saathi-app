package app.saathi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Keeps the Hindi file in step with the English one: every translatable string and plural is there, with the same
 * placeholders, and nothing shouts. Lint also checks missing translations; this runs with the unit tests.
 */
class StringsParityTest {
    private class Entry(val plural: Boolean, val texts: Map<String, String>)

    private fun load(dir: String): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val out = linkedMapOf<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            when (e.tagName) {
                "string" -> out[e.getAttribute("name")] = Entry(false, mapOf("" to e.textContent))
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    out[e.getAttribute("name")] = Entry(true, (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    })
                }
            }
        }
        return out
    }

    private val en = load("values")
    private val hi = load("values-hi")
    private val placeholder = Regex("""%\d\$[sd]""")

    private fun marks(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun hindiHasEveryEnglishString() {
        assertEquals("missing in values-hi", emptyList<String>(), (en.keys - hi.keys).toList())
        assertEquals("extra in values-hi", emptyList<String>(), (hi.keys - en.keys).toList())
    }

    @Test
    fun placeholdersMatchInEveryForm() {
        for ((name, e) in en) {
            val h = hi.getValue(name)
            assertEquals("$name: string or plurals differs", e.plural, h.plural)
            for ((quantity, text) in e.texts) {
                val other = h.texts[quantity]
                assertTrue("$name: Hindi has no '$quantity' form", other != null)
                assertEquals("$name ($quantity): placeholders differ", marks(text), marks(other!!))
            }
        }
    }

    @Test
    fun hindiPluralsHaveOneAndOther() {
        for ((name, h) in hi) if (h.plural) {
            assertTrue("$name: needs one and other", h.texts.keys.containsAll(listOf("one", "other")))
        }
    }

    @Test
    fun noExclamationMarks() {
        for ((lang, strings) in listOf("en" to en, "hi" to hi)) for ((name, e) in strings) for (text in e.texts.values) {
            assertFalse("$lang $name has an exclamation mark", text.contains('!'))
        }
    }
}
