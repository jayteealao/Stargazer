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

package uk.adedamola.stargazer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.adedamola.stargazer.data.auth.LegacyAccountIdentifier
import javax.inject.Inject

@HiltAndroidApp
class Stargazer : Application() {

    @Inject
    lateinit var legacyIdentifier: LegacyAccountIdentifier

    override fun onCreate() {
        super.onCreate()
        // An update from the pasted-token version: learn whose data is on the phone, once.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { legacyIdentifier.identify() }
        }
    }
}
