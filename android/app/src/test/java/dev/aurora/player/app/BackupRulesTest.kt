package dev.aurora.player.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Listening history must not leave the device through Auto Backup.
 *
 * The app allows backup, which is what makes settings survive a reinstall. Without
 * exclusions that same mechanism copies aurora-database - history, completions and skips -
 * into the person's Google Drive, which AGENTS.md rules out. Nothing in the build fails if
 * these rules are deleted, so they are asserted here.
 */
class BackupRulesTest {

    private fun excludedPaths(file: String, section: String?): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/$file"))
        val root = section
            ?.let { document.getElementsByTagName(it).item(0) }
            ?: document.documentElement
        val excludes = mutableSetOf<String>()
        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeName != "exclude") continue
            excludes += node.attributes.getNamedItem("path").nodeValue
        }
        return excludes
    }

    @Test
    fun `cloud backup excludes the library database and the secrets`() {
        for ((file, section) in listOf(
            "backup_rules.xml" to null,
            "data_extraction_rules.xml" to "cloud-backup"
        )) {
            val excluded = excludedPaths(file, section)
            assertTrue("$file must exclude aurora-database", "aurora-database" in excluded)
            assertTrue("$file must exclude the write-ahead log", "aurora-database-wal" in excluded)
            assertTrue("$file must exclude the secrets", "aurora_secure.xml" in excluded)
        }
    }

    @Test
    fun `device transfer keeps the library but never the secrets`() {
        // A direct move to the person's next phone: the history travels, because losing it
        // would be the worse failure and it never touches a server. The secrets cannot,
        // since the Keystore key that decrypts them does not move.
        val excluded = excludedPaths("data_extraction_rules.xml", "device-transfer")
        assertTrue("secrets must not transfer", "aurora_secure.xml" in excluded)
        assertTrue("the library should transfer", "aurora-database" !in excluded)
    }
}
