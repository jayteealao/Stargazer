/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.adedamola.stargazer.data.local.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.adedamola.stargazer.data.local.database.AccountDao
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.GitHubProfileDao
import uk.adedamola.stargazer.data.local.database.StarListDao
import uk.adedamola.stargazer.data.local.database.RepositoryDao
import uk.adedamola.stargazer.data.local.database.RepositoryTagDao
import uk.adedamola.stargazer.data.local.database.SearchPresetDao
import uk.adedamola.stargazer.data.local.database.StargazerDao
import uk.adedamola.stargazer.data.local.database.SyncMetadataDao
import uk.adedamola.stargazer.data.local.database.TagDao
import javax.inject.Singleton

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add starredAt column to repositories table
        db.execSQL("ALTER TABLE repositories ADD COLUMN starredAt INTEGER DEFAULT NULL")

        // Add new columns to sync_metadata table
        db.execSQL("ALTER TABLE sync_metadata ADD COLUMN isInitialSyncComplete INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_metadata ADD COLUMN newestStarredAt INTEGER DEFAULT NULL")
    }
}

/** Adds the account record, GitHub profile and star-list tables. SQL copied from schema 6.json. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `account` (`userId` INTEGER NOT NULL, `login` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `github_profile` (`userId` INTEGER NOT NULL, `login` TEXT NOT NULL, `name` TEXT, `avatarUrl` TEXT NOT NULL, `bio` TEXT, `followers` INTEGER NOT NULL, `following` INTEGER NOT NULL, `publicRepos` INTEGER NOT NULL, `starredCount` INTEGER, `location` TEXT, `company` TEXT, `blog` TEXT, `htmlUrl` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `star_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `isPrivate` INTEGER NOT NULL, `itemCount` INTEGER NOT NULL, `position` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `star_list_items` (`listId` TEXT NOT NULL, `repositoryId` INTEGER NOT NULL, PRIMARY KEY(`listId`, `repositoryId`), FOREIGN KEY(`listId`) REFERENCES `star_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_star_list_items_repositoryId` ON `star_list_items` (`repositoryId`)")
    }
}


@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Provides
    fun provideStargazerDao(appDatabase: AppDatabase): StargazerDao {
        return appDatabase.stargazerDao()
    }

    @Provides
    fun provideRepositoryDao(appDatabase: AppDatabase): RepositoryDao {
        return appDatabase.repositoryDao()
    }

    @Provides
    fun provideTagDao(appDatabase: AppDatabase): TagDao {
        return appDatabase.tagDao()
    }

    @Provides
    fun provideRepositoryTagDao(appDatabase: AppDatabase): RepositoryTagDao {
        return appDatabase.repositoryTagDao()
    }

    @Provides
    fun provideSearchPresetDao(appDatabase: AppDatabase): SearchPresetDao {
        return appDatabase.searchPresetDao()
    }

    @Provides
    fun provideSyncMetadataDao(appDatabase: AppDatabase): SyncMetadataDao {
        return appDatabase.syncMetadataDao()
    }

    @Provides
    fun provideAccountDao(appDatabase: AppDatabase): AccountDao {
        return appDatabase.accountDao()
    }

    @Provides
    fun provideGitHubProfileDao(appDatabase: AppDatabase): GitHubProfileDao {
        return appDatabase.gitHubProfileDao()
    }

    @Provides
    fun provideStarListDao(appDatabase: AppDatabase): StarListDao {
        return appDatabase.starListDao()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "Stargazer"
            )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration(false)
            .build()
    }
}
