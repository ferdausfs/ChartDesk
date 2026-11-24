package com.chartdesk.app.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `same password and salt produce the same hash`() {
        val salt = PasswordHasher.newSalt()
        val a = PasswordHasher.hash("s3cret-pass", salt, iterations = 1_000)
        val b = PasswordHasher.hash("s3cret-pass", salt, iterations = 1_000)
        assertTrue(a == b)
    }

    @Test
    fun `different salts produce different hashes`() {
        val a = PasswordHasher.hash("s3cret-pass", PasswordHasher.newSalt(), iterations = 1_000)
        val b = PasswordHasher.hash("s3cret-pass", PasswordHasher.newSalt(), iterations = 1_000)
        assertNotEquals(a, b)
    }

    @Test
    fun `verify accepts the right password and rejects the wrong one`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("correct horse", salt, iterations = 1_000)
        assertTrue(PasswordHasher.verify("correct horse", salt, hash, iterations = 1_000))
        assertFalse(PasswordHasher.verify("wrong horse", salt, hash, iterations = 1_000))
    }

    @Test
    fun `salt is 16 random bytes`() {
        val a = PasswordHasher.newSalt()
        val b = PasswordHasher.newSalt()
        assertTrue(a.size == 16)
        assertFalse(a.contentEquals(b))
    }
}
