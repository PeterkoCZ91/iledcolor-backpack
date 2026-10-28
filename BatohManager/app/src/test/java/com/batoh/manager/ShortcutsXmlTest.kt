package com.batoh.manager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutsXmlTest {
    private val xml = File("src/main/res/xml/shortcuts.xml").readText()

    @Test fun panelShortcutUsesPlainDeepLink() {
        assertTrue(xml.contains("android:data=\"gifpack://backpack\""))
    }

    @Test fun shortcutsNeverAutoStartAnUpload() {
        assertFalse(xml.contains("auto_test"))
    }

    @Test fun shortcutTargetsExistingActivity() {
        assertTrue(xml.contains("android:targetClass=\"com.batoh.manager.MainActivity\""))
    }
}
