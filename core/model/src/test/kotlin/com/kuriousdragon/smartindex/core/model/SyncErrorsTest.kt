package com.kuriousdragon.smartindex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncErrorsTest {
    private class SQLiteFullException(message: String? = null) : RuntimeException(message)

    @Test
    fun `an exception named SQLiteFullException maps to storage full`() {
        assertThat(SyncErrors.code(SQLiteFullException())).isEqualTo(SyncErrors.STORAGE_FULL)
    }

    @Test
    fun `a nested cause mentioning SQLITE_FULL maps to storage full`() {
        val t = RuntimeException("outer", IllegalStateException("inner", RuntimeException("SQLITE_FULL: disk")))
        assertThat(SyncErrors.code(t)).isEqualTo(SyncErrors.STORAGE_FULL)
    }

    @Test
    fun `ENOSPC and no space left messages map to storage full`() {
        assertThat(SyncErrors.code(java.io.IOException("write failed: ENOSPC"))).isEqualTo(SyncErrors.STORAGE_FULL)
        assertThat(SyncErrors.code(java.io.IOException("No space left on device"))).isEqualTo(SyncErrors.STORAGE_FULL)
    }

    @Test
    fun `a security exception maps to its simple class name`() {
        assertThat(SyncErrors.code(SecurityException("secret"))).isEqualTo("SecurityException")
    }

    @Test
    fun `an anonymous exception maps to Throwable`() {
        val anonymous = object : RuntimeException("x") {}
        assertThat(SyncErrors.code(anonymous)).isEqualTo("Throwable")
    }

    @Test
    fun `the returned code never contains the exception message`() {
        val message = "content://sms/42 +15551234567 Alice"
        val code = SyncErrors.code(IllegalArgumentException(message))
        assertThat(code).isEqualTo("IllegalArgumentException")
        assertThat(code).doesNotContain("Alice")
        assertThat(code).doesNotContain("5551234567")
    }

    @Test
    fun `storage full is detected inside composite codes`() {
        assertThat(SyncErrors.isStorageFullCode("sms:storage_full:X")).isTrue()
        assertThat(SyncErrors.isStorageFullCode("sms:write:SQLiteFullException")).isTrue()
        assertThat(SyncErrors.isStorageFullCode("STORAGE_FULL")).isTrue()
    }

    @Test
    fun `null and unrelated codes are not storage full`() {
        assertThat(SyncErrors.isStorageFullCode(null)).isFalse()
        assertThat(SyncErrors.isStorageFullCode("SecurityException")).isFalse()
    }
}
