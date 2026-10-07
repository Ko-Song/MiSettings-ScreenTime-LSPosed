package com.kosong.misettings

import org.junit.Assert.*
import org.junit.Test
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider

/** Authored JVM fixtures for the supplied public signatures, not target APK code or runtime tests. */
class HostModelsTest {
    @Test
    fun nullableVisualDataAndOriginalStatisticsArePreserved() = Fixture().use { f ->
        val originalStatistics = f.instance("ScreenTimeDetails")
        val replacement = f.instance("ScreenTimeDetails")
        val business = f.business()
        val original = f.page(null, originalStatistics, business)
        val copy = HostModels.copyDetailPage(original, replacement)
        assertNotSame(original, copy)
        assertNull(HostModels.read(copy, "getVisualHealthDetail"))
        assertSame(business, HostModels.read(copy, "getBusiness"))
        assertSame(replacement, HostModels.read(copy, "getScreenTimeDetails"))
        assertSame(originalStatistics, HostModels.read(original, "getScreenTimeDetails"))
    }

    @Test
    fun extraGettersAndCopyOverloadsCannotRedirectTheCopy() = Fixture().use { f ->
        val visual = f.instance("VisualHealthDetails")
        val business = f.business()
        val original = f.page(visual, null, business)
        val replacement = f.instance("ScreenTimeDetails")
        val copy = HostModels.copyDetailPage(original, replacement)
        assertSame(visual, HostModels.read(copy, "getVisualHealthDetail"))
        assertSame(business, HostModels.read(copy, "getBusiness"))
        assertNull(HostModels.read(original, "getScreenTimeDetails"))
    }

    @Test
    fun missingNamedGetterRejectsBindingEvenWithAnAlternativeOfTheSameType() {
        Fixture(renameVisualGetter = true).use { f ->
            assertThrows(NoSuchMethodException::class.java) { HostModels.verifyDetailPage(f.loader) }
        }
    }

    @Test
    fun wrongGetterReturnTypeRejectsBinding() {
        Fixture(wrongBusinessReturn = true).use { f ->
            assertThrows(IllegalArgumentException::class.java) { HostModels.verifyDetailPage(f.loader) }
        }
    }

    @Test
    fun missingExactCopyRejectsBindingDespiteCompatibleObjectOverload() {
        Fixture(renameExactCopy = true).use { f ->
            assertThrows(NoSuchMethodException::class.java) { HostModels.verifyDetailPage(f.loader) }
        }
    }

    @Test
    fun throwingGetterDoesNotBecomeNullableData() = Fixture().use { f ->
        val original = f.page(null, f.instance("ScreenTimeDetails"), f.business())
        original.javaClass.getField("throwOnVisual").setBoolean(null, true)
        val error = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            HostModels.copyDetailPage(original, f.instance("ScreenTimeDetails"))
        }
        assertTrue(error.cause is IllegalStateException)
    }

    @Test
    fun sameNamesInDifferentClassLoadersNeverMix() {
        Fixture().use { first ->
            Fixture().use { second ->
                val firstType = HostModels.verifyDetailPage(first.loader)
                val secondType = HostModels.verifyDetailPage(second.loader)
                assertNotSame(firstType, secondType)
                val original = first.page(null, null, first.business())
                assertThrows(IllegalArgumentException::class.java) {
                    HostModels.copyDetailPage(original, second.instance("ScreenTimeDetails"))
                }
                val secondPage = second.page(null, null, second.business())
                val copied = HostModels.copyDetailPage(secondPage, second.instance("ScreenTimeDetails"))
                assertSame(secondType, copied.javaClass)
            }
        }
    }

    @Test
    fun primitiveCopyContractPreservesLongPrecisionAndRejectsWrongBoxing(): Unit = Fixture().use { f ->
        val type = f.loader.loadClass(Fixture.PAGE + "ScreenTimeDetails" + '$' + "DeviceUsageDetails")
        val original = type.getConstructor().newInstance()
        val total = Int.MAX_VALUE.toLong() + 100L
        val buckets = listOf(total)
        val copy = HostModels.copy(original, arrayOf(total, buckets, total, total, 7L))
        assertEquals(total, HostModels.read(copy, "getTotalDuration"))
        assertSame(buckets, HostModels.read(copy, "getDetail"))
        assertEquals(0L, HostModels.read(original, "getTotalDuration"))
        assertThrows(IllegalArgumentException::class.java) {
            HostModels.copy(original, arrayOf("invalid", buckets, total, total, 7L))
        }
    }

    @Test
    fun unknownModelDoesNotFallBackToSignatureScanning() {
        assertThrows(IllegalStateException::class.java) { HostModels.copy(Any(), emptyArray()) }
    }

    private class Fixture(
        renameVisualGetter: Boolean = false,
        wrongBusinessReturn: Boolean = false,
        renameExactCopy: Boolean = false,
    ) : AutoCloseable {
        private val root = Files.createTempDirectory("misettings-contract-")
        val loader: URLClassLoader

        init {
            val visualGetter = if (renameVisualGetter) "getUnverifiedVisual" else "getVisualHealthDetail"
            val businessReturn = if (wrongBusinessReturn) "Object" else "c9.b"
            val copyName = if (renameExactCopy) "unverifiedCopy" else "copy"
            val sources = mapOf(
                "c9/b.java" to "package c9; public final class b {}",
                "com/xiaomi/misettings/base/model/page/VisualHealthDetails.java" to
                    "package ${PAGE.dropLast(1)}; public final class VisualHealthDetails {}",
                "com/xiaomi/misettings/base/model/page/ScreenTimeDetails.java" to """
                    package ${PAGE.dropLast(1)};
                    import java.util.List;
                    public final class ScreenTimeDetails {
                        public static final class DeviceUsageDetails {
                            private final long total;
                            private final List<Long> detail;
                            public DeviceUsageDetails() { this(0L, null); }
                            private DeviceUsageDetails(long total, List<Long> detail) {
                                this.total = total; this.detail = detail;
                            }
                            public DeviceUsageDetails copy(long total, List<Long> detail, long max, long avg, long last) {
                                return new DeviceUsageDetails(total, detail);
                            }
                            public DeviceUsageDetails copy(Object a, Object b, Object c, Object d, Object e) {
                                throw new AssertionError("Unverified overload invoked");
                            }
                            public long getTotalDuration() { return total; }
                            public List<Long> getDetail() { return detail; }
                        }
                    }
                """.trimIndent(),
                "com/xiaomi/misettings/base/model/page/DetailPageModel.java" to """
                    package ${PAGE.dropLast(1)};
                    public final class DetailPageModel {
                        public static boolean throwOnVisual;
                        private final VisualHealthDetails visual;
                        private final ScreenTimeDetails screen;
                        private final c9.b business;
                        public DetailPageModel(VisualHealthDetails visual, ScreenTimeDetails screen, c9.b business) {
                            this.visual = visual; this.screen = screen;
                            this.business = java.util.Objects.requireNonNull(business);
                        }
                        public VisualHealthDetails $visualGetter() {
                            if (throwOnVisual) throw new IllegalStateException("getter failed");
                            return visual;
                        }
                        public VisualHealthDetails getDecoyVisual() { return new VisualHealthDetails(); }
                        public ScreenTimeDetails getScreenTimeDetails() { return screen; }
                        public $businessReturn getBusiness() { return business; }
                        public c9.b getDecoyBusiness() { return new c9.b(); }
                        public DetailPageModel $copyName(VisualHealthDetails v, ScreenTimeDetails s, c9.b b) {
                            return new DetailPageModel(v, s, b);
                        }
                        public DetailPageModel copy(Object v, Object s, Object b) {
                            throw new AssertionError("Unverified overload invoked");
                        }
                    }
                """.trimIndent(),
            )
            val files = sources.map { (name, source) ->
                val path = root.resolve(name)
                Files.createDirectories(path.parent)
                Files.writeString(path, source)
                path.toFile()
            }
            val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "Run tests with JDK 17" }
            compiler.getStandardFileManager(null, null, Charsets.UTF_8).use { manager ->
                val task = compiler.getTask(null, manager, null, listOf("-d", root.toString()), null, manager.getJavaFileObjectsFromFiles(files))
                check(task.call()) { "Fixture compilation failed" }
            }
            loader = URLClassLoader(arrayOf(root.toUri().toURL()), null)
        }

        fun instance(name: String): Any = loader.loadClass(PAGE + name).getConstructor().newInstance()
        fun business(): Any = loader.loadClass("c9.b").getConstructor().newInstance()
        fun page(visual: Any?, screen: Any?, business: Any): Any = loader.loadClass(PAGE + "DetailPageModel")
            .getConstructor(loader.loadClass(PAGE + "VisualHealthDetails"), loader.loadClass(PAGE + "ScreenTimeDetails"), loader.loadClass("c9.b"))
            .newInstance(visual, screen, business)

        override fun close() {
            loader.close()
            root.toFile().deleteRecursively()
        }

        companion object {
            const val PAGE = "com.xiaomi.misettings.base.model.page."
        }
    }
}
