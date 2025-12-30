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

package uk.adedamola.stargazer.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Stargazer::class,
        RepositoryEntity::class,
        Tag::class,
        RepositoryTag::class,
        SearchPreset::class,
        SyncMetadata::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stargazerDao(): StargazerDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun tagDao(): TagDao
    abstract fun repositoryTagDao(): RepositoryTagDao
    abstract fun searchPresetDao(): SearchPresetDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
