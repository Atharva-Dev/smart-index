package com.kuriousdragon.smartindex.source.telephony

import com.kuriousdragon.smartindex.core.model.CountryIsoSource
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.source.telephony.calls.CallLogIndexer
import com.kuriousdragon.smartindex.source.telephony.common.ContentResolverProviders
import com.kuriousdragon.smartindex.source.telephony.common.SystemTelephonyClock
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyClock
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.mms.MmsIndexer
import com.kuriousdragon.smartindex.source.telephony.number.TelephonyCountryIsoSource
import com.kuriousdragon.smartindex.source.telephony.sms.SmsIndexer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelephonyIo

/** `PhoneNumberNormalizer` and `CountryIsoSource` are bound only here; the store and matcher inject them. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelephonyModule {
    @Binds abstract fun providers(impl: ContentResolverProviders): TelephonyProviders

    @Binds abstract fun country(impl: TelephonyCountryIsoSource): CountryIsoSource

    @Binds abstract fun clock(impl: SystemTelephonyClock): TelephonyClock

    @Binds @IntoSet
    abstract fun callLog(impl: CallLogIndexer): SourceIndexer

    @Binds @IntoSet
    abstract fun sms(impl: SmsIndexer): SourceIndexer

    @Binds @IntoSet
    abstract fun mms(impl: MmsIndexer): SourceIndexer

    companion object {
        @Provides
        @Singleton
        fun normalizer(country: CountryIsoSource): PhoneNumberNormalizer = DefaultPhoneNumberNormalizer(country)

        @Provides
        @TelephonyIo
        fun io(): CoroutineDispatcher = Dispatchers.IO
    }
}
