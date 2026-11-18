package com.kuriousdragon.smartindex.source.media.di

import javax.inject.Qualifier

/** The dispatcher the media indexer reads MediaStore on (bound by the Hilt module of the module ticket). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaIo
