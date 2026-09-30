package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dead end this exists to prevent.
 *
 * TDLib's database is encrypted with a key kept in Android Keystore. Lose the key while the
 * database survives and nothing on the device will ever open it - and the app used to generate a
 * replacement, store it as though it were the right one, and report "error 401: Wrong database
 * encryption key" on every launch afterwards, with no way out but clearing its storage.
 */
class TelegramDatabaseKeyTest {

    @Test
    fun `a database with no key left to open it is orphaned`() {
        assertTrue(telegramDatabaseIsOrphaned(hasStoredKey = false, databaseExists = true))
    }

    @Test
    fun `a database whose key is still held is left alone`() {
        assertFalse(telegramDatabaseIsOrphaned(hasStoredKey = true, databaseExists = true))
    }

    /** A first launch has no database to throw away, and must not be treated as a repair. */
    @Test
    fun `a fresh install has nothing to orphan`() {
        assertFalse(telegramDatabaseIsOrphaned(hasStoredKey = false, databaseExists = false))
        assertFalse(telegramDatabaseIsOrphaned(hasStoredKey = true, databaseExists = false))
    }

    @Test
    fun `the database error is recognised by what it says, not only by its code`() {
        assertTrue(isWrongDatabaseKey(401, "Wrong database encryption key"))
        assertTrue(isWrongDatabaseKey(401, "wrong database ENCRYPTION KEY"))
    }

    /**
     * 401 is Telegram's code for unauthorized, which a session that has simply ended reports too.
     * Throwing the database away over one of those would sign the user out for no reason.
     */
    @Test
    fun `an ordinary unauthorized answer is not a reason to delete anything`() {
        assertFalse(isWrongDatabaseKey(401, "Unauthorized"))
        assertFalse(isWrongDatabaseKey(401, "AUTH_KEY_UNREGISTERED"))
        assertFalse(isWrongDatabaseKey(400, "Wrong database encryption key"))
    }
}
