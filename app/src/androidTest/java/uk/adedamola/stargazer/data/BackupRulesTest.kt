package uk.adedamola.stargazer.data

import android.content.res.XmlResourceParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import uk.adedamola.stargazer.R
import androidx.datastore.preferences.preferencesDataStoreFile
import uk.adedamola.stargazer.data.auth.LegacyAccountStore
import uk.adedamola.stargazer.data.di.SecureStorageModule
import java.io.File

/** The backup rules exclude the access-key directory, and nothing else. */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keyRule = Rule(tag = "exclude", domain = "file", path = SecureStorageModule.KEY_DIR + "/")

    /** The earlier version's preferences file, which can hold its plain-text token. */
    private val oldTokenRule = Rule(
        tag = "exclude",
        domain = "file",
        path = "datastore/${LegacyAccountStore.FILE_NAME}.preferences_pb"
    )

    private data class Rule(val tag: String, val domain: String?, val path: String?)

    /** Collects include/exclude rules per section (cloud-backup, device-transfer, full-backup-content). */
    private fun rules(xmlRes: Int): Map<String, List<Rule>> {
        val result = linkedMapOf<String, MutableList<Rule>>()
        val parser: XmlResourceParser = context.resources.getXml(xmlRes)
        var section: String? = null
        parser.use {
            while (it.next() != XmlPullParser.END_DOCUMENT) {
                if (it.eventType != XmlPullParser.START_TAG) continue
                when (it.name) {
                    "cloud-backup", "device-transfer", "full-backup-content" -> {
                        section = it.name
                        result.getOrPut(it.name) { mutableListOf() }
                    }
                    "include", "exclude" -> result.getValue(section!!) += Rule(
                        tag = it.name,
                        domain = it.getAttributeValue(null, "domain"),
                        path = it.getAttributeValue(null, "path"),
                    )
                }
            }
        }
        return result
    }

    @Test
    fun onlyTheKeyDirectoryAndTheOldTokenFileAreExcluded() {
        val extraction = rules(R.xml.data_extraction_rules)
        assertEquals(setOf("cloud-backup", "device-transfer"), extraction.keys)
        assertEquals(listOf(keyRule, oldTokenRule), extraction.getValue("cloud-backup"))
        assertEquals(listOf(keyRule, oldTokenRule), extraction.getValue("device-transfer"))

        val fullBackup = rules(R.xml.backup_rules)
        assertEquals(setOf("full-backup-content"), fullBackup.keys)
        assertEquals(listOf(keyRule, oldTokenRule), fullBackup.getValue("full-backup-content"))
    }

    @Test
    fun theOldTokenFileIsTheExcludedPathAndTheMarkerFileIsNot() {
        val datastore = File(context.filesDir, "datastore").canonicalFile
        assertEquals(
            File(datastore, "${LegacyAccountStore.FILE_NAME}.preferences_pb"),
            context.preferencesDataStoreFile(LegacyAccountStore.FILE_NAME).canonicalFile
        )
        // The owner-unknown marker stays in backup, like the account record.
        assertTrue(LegacyAccountStore.MARKER_FILE_NAME != LegacyAccountStore.FILE_NAME)
    }

    @Test
    fun keyFileLiesInTheExcludedDirectory() {
        val keyFile = SecureStorageModule.keyFile(context).canonicalFile
        val excludedDir = File(context.filesDir, SecureStorageModule.KEY_DIR).canonicalFile
        assertEquals(excludedDir, keyFile.parentFile)
        assertTrue(keyFile.path.startsWith(excludedDir.path + File.separator))
    }
}
