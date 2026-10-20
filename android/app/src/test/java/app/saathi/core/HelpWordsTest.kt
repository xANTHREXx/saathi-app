package app.saathi.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpWordsTest {
    @Test fun englishHindiAndHinglish() {
        assertTrue(asksForHelp("please help"))
        assertTrue(asksForHelp("bachao, koi peecha kar raha hai"))
        assertTrue(asksForHelp("मदद करो"))
    }

    @Test fun calmWordsDoNotRaise() {
        assertFalse(asksForHelp("hi, just checking in"))
        assertFalse(asksForHelp("what's the helpline number?"))
    }
}
