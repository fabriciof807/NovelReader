package com.novelreader.i18n

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class I18nCoverageTest {

    private lateinit var allStringNames: Set<String>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rClass = Class.forName("com.novelreader.R\$string")
        allStringNames = rClass.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertThat(context.packageName).isNotEmpty()
    }

    @Test
    fun `every pt-BR string has an en counterpart`() {
        val enKeys = parseStringKeys("src/main/res/values-en/strings.xml")
        val missing = allStringNames - enKeys
        assertThat(missing).isEmpty()
    }

    private fun parseStringKeys(relativePath: String): Set<String> {
        val file = File(relativePath)
        assertThat(file.exists()).isTrue()
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = builder.parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .mapNotNull { nodes.item(it).attributes?.getNamedItem("name")?.nodeValue }
            .toSet()
    }
}
