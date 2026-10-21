package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {
    private val amma = Contact("Amma", "+919765250388", "anywhere")

    @Test
    fun normaliseTenDigitIndianMobile() {
        assertEquals("+919765250388", normalisePhone("9765250388"))
    }

    @Test
    fun normaliseZeroPrefixedEleven() {
        assertEquals("+919765250388", normalisePhone("09765250388"))
    }

    @Test
    fun normaliseNinetyOnePrefixedTwelve() {
        assertEquals("+919765250388", normalisePhone("919765250388"))
    }

    @Test
    fun normalisePlusNineOneWithSpacesAndDashes() {
        assertEquals("+919765250388", normalisePhone("+91 97652-50388"))
        assertEquals("+919765250388", normalisePhone(" +91 (97652) 50.388 "))
    }

    @Test
    fun normaliseDoubleZeroPrefix() {
        assertEquals("+447911123456", normalisePhone("0044 7911 123456"))
    }

    @Test
    fun normaliseFourDigitEmulatorNumber() {
        assertEquals("5554", normalisePhone("5554"))
    }

    @Test
    fun normaliseTooShortIsNull() {
        assertNull(normalisePhone("12345"))
        assertNull(normalisePhone("+1234567"))
        assertNull(normalisePhone(""))
    }

    @Test
    fun normaliseLettersIsNull() {
        assertNull(normalisePhone("97652abcde"))
        assertNull(normalisePhone("+91 9765250ABC"))
    }

    @Test
    fun normaliseTenDigitsStartingWithTwoIsNull() {
        assertNull(normalisePhone("2765250388"))
    }

    @Test
    fun formatPhoneIndian() {
        assertEquals("+91 97652 50388", formatPhone("+919765250388"))
    }

    @Test
    fun formatPhoneOtherUnchanged() {
        assertEquals("+447911123456", formatPhone("+447911123456"))
        assertEquals("5554", formatPhone("5554"))
    }

    @Test
    fun isAnywhereMatchesTheTagLoosely() {
        assertTrue(isAnywhere("anywhere"))
        assertTrue(isAnywhere(" Anywhere "))
    }

    @Test
    fun isAnywhereFalseForANamedCity() {
        assertFalse(isAnywhere(" Bengaluru "))
    }

    @Test
    fun addPendingBothBlankLeavesListUnchanged() {
        assertEquals(PendingResult.Ok(listOf(amma)), addPending(listOf(amma), " ", "", "Pune"))
    }

    @Test
    fun addPendingNameWithoutPhone() {
        assertEquals(
            PendingResult.Problem(SetupProblem(SetupField.CONTACT_PHONE, ProblemKind.PHONE_MISSING, "Riya")),
            addPending(emptyList(), " Riya ", "  ", ""),
        )
    }

    @Test
    fun addPendingIncompletePhone() {
        assertEquals(
            PendingResult.Problem(SetupProblem(SetupField.CONTACT_PHONE, ProblemKind.PHONE_INCOMPLETE)),
            addPending(emptyList(), "Riya", "12345", ""),
        )
    }

    @Test
    fun addPendingPhoneWithoutName() {
        assertEquals(
            PendingResult.Problem(SetupProblem(SetupField.CONTACT_NAME, ProblemKind.NAME_MISSING)),
            addPending(emptyList(), " ", "9876543210", ""),
        )
    }

    @Test
    fun addPendingValidAddsNormalisedContactWithTrimmedCity() {
        assertEquals(
            PendingResult.Ok(listOf(amma, Contact("Riya", "+919876543210", "Bengaluru"))),
            addPending(listOf(amma), " Riya ", "98765 43210", " Bengaluru "),
        )
    }

    @Test
    fun addPendingBlankCityDefaultsToAnywhere() {
        assertEquals(
            PendingResult.Ok(listOf(Contact("Riya", "+919876543210", ANYWHERE))),
            addPending(emptyList(), "Riya", "9876543210", "  "),
        )
    }

    @Test
    fun addPendingDuplicatePhoneIsNotAddedTwice() {
        assertEquals(
            PendingResult.Ok(listOf(amma)),
            addPending(listOf(amma), "Mum", "+91 97652 50388", "Pune"),
        )
    }

    @Test
    fun setupProblemBlankName() {
        assertEquals(
            SetupProblem(SetupField.YOUR_NAME, ProblemKind.YOUR_NAME_MISSING),
            setupProblem("  ", listOf(amma)),
        )
    }

    @Test
    fun setupProblemNoContacts() {
        assertEquals(
            SetupProblem(SetupField.CONTACTS, ProblemKind.NO_CONTACTS),
            setupProblem("Asha", emptyList()),
        )
    }

    @Test
    fun setupProblemNoneWhenReady() {
        assertNull(setupProblem("Asha", listOf(amma)))
    }
}
