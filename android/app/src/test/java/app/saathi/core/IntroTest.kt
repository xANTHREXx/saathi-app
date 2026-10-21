package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class IntroTest {
    private val amma = Contact("Amma", "+919765250388", "Pune")
    private val riya = Contact("Riya", "98450 12345", ANYWHERE)

    @Test
    fun everyoneIsNewAtFirst() {
        assertEquals(listOf(amma, riya), toIntroduce(listOf(amma, riya), emptyList()))
    }

    @Test
    fun introducedNumbersAreSkippedHoweverTheyWereTyped() {
        // Riya was introduced as "+919845012345"; she is now saved as "98450 12345".
        assertEquals(listOf(amma), toIntroduce(listOf(amma, riya), listOf("+919845012345")))
    }

    @Test
    fun renamingAContactDoesNotTextThemAgain() {
        assertEquals(emptyList<Contact>(), toIntroduce(listOf(amma.copy(name = "Aai")), listOf(introKey(amma))))
    }

    @Test
    fun sameNumberTwiceIsTextedOnce() {
        val again = Contact("Mum", "09765250388", "Pune")
        assertEquals(listOf(amma), toIntroduce(listOf(amma, again), emptyList()))
    }

    @Test
    fun blankNumbersAreLeftOut() {
        assertEquals(listOf(amma), toIntroduce(listOf(amma, Contact("Chinu", " ", "Pune")), emptyList()))
    }
}
