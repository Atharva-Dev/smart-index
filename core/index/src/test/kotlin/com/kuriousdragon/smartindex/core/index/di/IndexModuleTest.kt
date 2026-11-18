package com.kuriousdragon.smartindex.core.index.di

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stands in for the telephony module, which binds the real [PhoneNumberNormalizer] (IndexModule must not). */
@Module
@InstallIn(SingletonComponent::class)
object FakeNumbersModule {
    @Provides
    @Singleton
    fun numbers(): PhoneNumberNormalizer =
        object : PhoneNumberNormalizer {
            override fun normalize(raw: String?): String? = raw?.filter { it.isDigit() }?.ifEmpty { null }

            override fun digitsOnly(raw: String?): String? = normalize(raw)
        }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class IndexModuleTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var writer: IndexWriter

    @Inject lateinit var query: IndexQuery

    @Inject lateinit var db: IndexDatabase

    @Inject lateinit var dbAgain: IndexDatabase

    @Before
    fun inject() = hilt.inject()

    @After
    fun close() = db.close()

    @Test
    fun `IndexWriter resolves`() {
        assertThat(writer).isNotNull()
    }

    @Test
    fun `IndexQuery resolves`() {
        assertThat(query).isNotNull()
    }

    @Test
    fun `the database is a singleton`() {
        assertThat(dbAgain).isSameInstanceAs(db)
    }
}
