package com.batoh.feature.backpack

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the sequence plurals exist in both locales with the expected Czech forms (JVM, parses the XML). */
class SequencePluralsTest {
    private val names = listOf("chain_seq_info", "chain_seq_warn_count", "backpack_sequence_name")

    private fun load(dir: String): Map<String, Map<String, String>> {
        val file = File("src/main/res/$dir/strings_gif_sequence.xml")
        assertTrue("Missing $file", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val plurals = doc.getElementsByTagName("plurals")
        return (0 until plurals.length).associate { i ->
            val node = plurals.item(i) as org.w3c.dom.Element
            val items = node.getElementsByTagName("item")
            node.getAttribute("name") to (0 until items.length).associate { j ->
                val item = items.item(j) as org.w3c.dom.Element
                item.getAttribute("quantity") to item.textContent
            }
        }
    }

    @Test fun everyPluralExistsInBothLocales() {
        val cs = load("values")
        val en = load("values-en")
        for (name in names) {
            assertTrue("cs missing $name", cs.containsKey(name))
            assertTrue("en missing $name", en.containsKey(name))
            assertEquals(setOf("one", "few", "many", "other"), cs.getValue(name).keys)
            assertEquals(setOf("one", "other"), en.getValue(name).keys)
        }
    }

    @Test fun czechFormsForOneTwoFive() {
        val cs = load("values").getValue("backpack_sequence_name")
        assertEquals("Sekvence (1 program)", cs.getValue("one").format(1))
        assertEquals("Sekvence (2 programy)", cs.getValue("few").format(2))
        assertEquals("Sekvence (5 programů)", cs.getValue("other").format(5))
        assertEquals(cs.getValue("many"), cs.getValue("other"))
    }

    @Test fun englishFormsForOneAndMany() {
        val en = load("values-en").getValue("backpack_sequence_name")
        assertEquals("Sequence (1 programme)", en.getValue("one").format(1))
        assertEquals("Sequence (5 programmes)", en.getValue("other").format(5))
    }
}
