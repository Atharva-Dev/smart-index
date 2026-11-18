package com.kuriousdragon.smartindex.core.permissions.di

import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.permissions.AndroidSystemPermissions
import com.kuriousdragon.smartindex.core.permissions.DefaultPermissionService
import com.kuriousdragon.smartindex.core.permissions.SystemPermissions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionsModule {
    @Binds
    abstract fun service(impl: DefaultPermissionService): PermissionService

    @Binds
    abstract fun system(impl: AndroidSystemPermissions): SystemPermissions
}
