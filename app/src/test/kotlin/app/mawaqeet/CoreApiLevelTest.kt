package app.mawaqeet

import app.mawaqeet.core.PrayerCore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `:core` is a plain Kotlin module, so Android lint never checks its calls against the app's minSdk. A Java library
 * method Android only added later (`LocalDate.ofInstant` is Android 14) builds and passes every JVM test, then crashes
 * the app at launch on older phones: a Samsung Note 8 on Android 9 would not open. This reads `:core`'s bytecode and
 * checks every `java.*` call against the SDK's API database.
 */
class CoreApiLevelTest {
    @Test
    fun `core calls no Java library method newer than the app's minSdk`() {
        val database = apiVersions()
        assumeTrue("No Android SDK API database here", database != null)
        val since = parse(database!!)
        val tooNew = coreCalls().mapNotNull { call ->
            val level = since[call] ?: return@mapNotNull null
            if (level > MIN_SDK) "$call (API $level)" else null
        }.sorted()
        assertEquals(emptyList<String>(), tooNew)
    }

    /** Every `java.*` method called from `:core`, as `java/time/LocalDate.ofInstant(Ljava/time/Instant;…)…`. */
    private fun coreCalls(): Set<String> {
        val calls = mutableSetOf<String>()
        val visitor = object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?) =
                object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                        if (owner.startsWith("java/")) calls += "$owner.$name$descriptor"
                    }
                }
        }
        val location = File(PrayerCore::class.java.protectionDomain.codeSource.location.toURI())
        if (location.isDirectory) {
            location.walk().filter { it.extension == "class" }.forEach { ClassReader(it.readBytes()).accept(visitor, 0) }
        } else {
            ZipFile(location).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                    zip.getInputStream(entry).use { ClassReader(it.readBytes()).accept(visitor, 0) }
                }
            }
        }
        assumeTrue("No core classes found", calls.isNotEmpty())
        return calls
    }

    /** The API level each method was added in, keyed like [coreCalls]; a member without its own level has its class's. */
    private fun parse(file: File): Map<String, Int> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val classes = document.getElementsByTagName("class")
        val since = HashMap<String, Int>()
        for (i in 0 until classes.length) {
            val type = classes.item(i) as Element
            val name = type.getAttribute("name")
            val classLevel = level(type.getAttribute("since"))
            val methods = type.getElementsByTagName("method")
            for (j in 0 until methods.length) {
                val method = methods.item(j) as Element
                since["$name.${method.getAttribute("name")}"] = level(method.getAttribute("since")) ?: classLevel ?: 1
            }
        }
        return since
    }

    private fun level(text: String): Int? = text.substringBefore('.').toIntOrNull()

    /** The newest platform's api-versions.xml in the SDK (ANDROID_HOME, or sdk.dir in local.properties). */
    private fun apiVersions(): File? {
        val local = sequenceOf(File("local.properties"), File("../local.properties")).firstOrNull { it.exists() }
            ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("sdk.dir") }
        val sdk = sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), local)
            .filterNotNull().map(::File).firstOrNull { it.isDirectory } ?: return null
        return File(sdk, "platforms").listFiles().orEmpty()
            .map { File(it, "data/api-versions.xml") }.filter { it.exists() }
            .maxByOrNull { it.parentFile.parentFile.name }
    }

    private companion object {
        /** app/build.gradle.kts's minSdk. */
        const val MIN_SDK = 26
    }
}
