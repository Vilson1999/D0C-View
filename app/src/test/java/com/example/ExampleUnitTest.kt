package com.example

import com.example.data.DocAlignment
import com.example.data.DocumentCacheManager
import com.example.data.OfficeChartType
import com.example.data.OfficeXmlFormatParser
import com.example.data.OoxmlDocumentParser
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.PageViewMode
import com.example.model.SortOption
import com.example.service.BackgroundLifecyclePolicy
import com.example.viewmodel.DocumentQueryProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExampleUnitTest {

    private fun fakeDoc(
        name: String,
        type: DocType,
        size: Long,
        modified: Long,
        folder: String = "Documents",
        dotFolder: Boolean = false
    ) = DocumentItem(
        id = "/storage/$folder/$name",
        name = name,
        path = "/storage/$folder/$name",
        uriString = "file:///storage/$folder/$name",
        type = type,
        extension = name.substringAfterLast('.', ""),
        sizeBytes = size,
        lastModified = modified,
        parentFolderName = folder,
        isFromDotFolder = dotFolder,
        isFilePath = true
    )

    @Test
    fun docType_mapsAllSupportedExtensions() {
        assertEquals(DocType.PDF, DocType.fromFileName("report.pdf"))
        assertEquals(DocType.DOC, DocType.fromFileName("letter.doc"))
        assertEquals(DocType.DOC, DocType.fromFileName("letter.docx"))
        assertEquals(DocType.XML, DocType.fromFileName("config.xml"))
        assertEquals(DocType.PPT, DocType.fromFileName("deck.ppt"))
        assertEquals(DocType.PPT, DocType.fromFileName("deck.pptx"))
        assertEquals(DocType.TEXT, DocType.fromFileName("notes.txt"))
        assertEquals(DocType.TEXT, DocType.fromFileName(".hidden_readme.md"))
        assertNull(DocType.fromFileName("photo.png"))
    }

    @Test
    fun appSettings_defaultsToSinglePageViewMode() {
        val defaults = AppSettings()
        assertEquals(PageViewMode.SINGLE_PAGE, defaults.defaultPageViewMode)
        assertTrue(defaults.autoHideReaderUi)
    }

    @Test
    fun documentCacheManager_savesAndLoadsIndexRoundTrip() {
        val tempDir = Files.createTempDirectory("docview_idx_test").toFile()
        try {
            val indexFile = File(tempDir, DocumentCacheManager.INDEX_FILE_NAME)
            val original = listOf(
                fakeDoc("Alpha\tReport.pdf", DocType.PDF, 2048L, 1700000000000L, folder = ".secret", dotFolder = true),
                fakeDoc("notes.txt", DocType.TEXT, 512L, 1700000005000L, folder = "Work")
            )
            DocumentCacheManager.saveIndexToFile(indexFile, original)
            val restored = DocumentCacheManager.loadIndexFromFile(indexFile)
            assertEquals(original, restored)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun documentCacheManager_prunesOrphanedExpiredTempAndLruCacheAutomatically() {
        val cacheRoot = Files.createTempDirectory("docview_prune_test").toFile()
        try {
            val pdfDir = File(cacheRoot, DocumentCacheManager.PDF_PAGES_DIR).apply { mkdirs() }
            val parsedDir = File(cacheRoot, DocumentCacheManager.PARSED_DOCS_DIR).apply { mkdirs() }
            val convertedDir = File(cacheRoot, DocumentCacheManager.CONVERTED_PDFS_DIR).apply { mkdirs() }
            val indexFile = File(cacheRoot, DocumentCacheManager.INDEX_FILE_NAME).apply {
                writeText("idx")
            }

            val activeDoc = fakeDoc("active.pdf", DocType.PDF, 1024L, 10_000L)
            val secondActiveDoc = fakeDoc("keep.docx", DocType.DOC, 2048L, 20_000L)
            val deletedDoc = fakeDoc("deleted.pdf", DocType.PDF, 512L, 5_000L)

            val activeKey = DocumentCacheManager.docKey(activeDoc)
            val secondKey = DocumentCacheManager.docKey(secondActiveDoc)
            val orphanKey = DocumentCacheManager.docKey(deletedDoc)

            val now = 1_000_000_000L
            val maxAgeMs = 7L * 24L * 60L * 60L * 1000L

            // Valid newer file (should survive)
            val activeRecentFile = File(pdfDir, "${activeKey}_p1_w900.webp").apply {
                writeBytes(ByteArray(40))
                setLastModified(now - 1000L)
            }
            // Valid converted PDF (older than activeRecentFile, evicted when over LRU budget)
            val activeConvertedPdf = File(convertedDir, "${secondKey}_doc_fs15.pdf").apply {
                writeBytes(ByteArray(20))
                setLastModified(now - 200_000L)
            }
            // Valid older file (still within maxAgeMs, oldest for LRU eviction when over budget)
            val activeOlderLruFile = File(parsedDir, "${secondKey}.dat").apply {
                writeBytes(ByteArray(60))
                setLastModified(now - 500_000L)
            }
            // Orphaned files for deleted document (must be deleted)
            val orphanFile = File(pdfDir, "${orphanKey}_p0_w900.webp").apply {
                writeBytes(ByteArray(20))
                setLastModified(now - 1000L)
            }
            val orphanConvertedPdf = File(convertedDir, "${orphanKey}_xml_fs15.pdf").apply {
                writeBytes(ByteArray(20))
                setLastModified(now - 1000L)
            }
            // Expired valid-key file (> maxAgeMs old, must be deleted)
            val expiredFile = File(pdfDir, "${activeKey}_p0_w900.webp").apply {
                writeBytes(ByteArray(20))
                setLastModified(now - maxAgeMs - 5000L)
            }
            // Leftover temp files (must be deleted)
            val strayRootTmp = File(cacheRoot, "legacy_image_cache.tmp").apply { writeBytes(ByteArray(15)) }
            val dirTmp = File(parsedDir, "${activeKey}.dat.tmp").apply { writeBytes(ByteArray(15)) }

            DocumentCacheManager.pruneCacheDirectory(
                cacheRoot = cacheRoot,
                validDocKeys = setOf(activeKey, secondKey),
                maxCacheBytes = 80L,
                maxAgeMs = maxAgeMs,
                nowMs = now
            )

            assertTrue("Index file must be preserved", indexFile.exists())
            assertFalse("Orphaned cache must be deleted", orphanFile.exists())
            assertFalse("Orphaned converted PDF must be deleted", orphanConvertedPdf.exists())
            assertFalse("Expired cache must be deleted", expiredFile.exists())
            assertFalse("Stray root cache must be deleted", strayRootTmp.exists())
            assertFalse("Leftover .tmp cache must be deleted", dirTmp.exists())
            assertFalse("Oldest LRU file exceeding budget must be evicted", activeOlderLruFile.exists())
            assertTrue("Valid recent converted PDF within budget must remain", activeConvertedPdf.exists())
            assertTrue("Most recently used active file within budget must remain", activeRecentFile.exists())
        } finally {
            cacheRoot.deleteRecursively()
        }
    }

    @Test
    fun queryProcessor_filtersSortsAndSearchesDocuments() {
        val docs = listOf(
            fakeDoc("zeta.pdf", DocType.PDF, 300, 1000L),
            fakeDoc("Alpha.pdf", DocType.PDF, 100, 3000L, folder = ".vault", dotFolder = true),
            fakeDoc("beta.docx", DocType.DOC, 500, 2000L, folder = "WorkDocs"),
            fakeDoc("config.xml", DocType.XML, 50, 4000L)
        )

        // Filter by DocType + Sort by Name ASC (case-insensitive)
        val pdfsByName = DocumentQueryProcessor.filterAndSort(
            documents = docs,
            query = "",
            sortOption = SortOption.NAME_ASC,
            typeFilter = DocType.PDF
        )
        assertEquals(listOf("Alpha.pdf", "zeta.pdf"), pdfsByName.map { it.name })

        // Sort by Name DESC
        val byNameDesc = DocumentQueryProcessor.filterAndSort(
            documents = docs,
            query = "",
            sortOption = SortOption.NAME_DESC
        )
        assertEquals(listOf("zeta.pdf", "config.xml", "beta.docx", "Alpha.pdf"), byNameDesc.map { it.name })

        // Sort by Size DESC & ASC
        val bySizeDesc = DocumentQueryProcessor.filterAndSort(
            documents = docs,
            query = "",
            sortOption = SortOption.SIZE_DESC
        )
        assertEquals(listOf("beta.docx", "zeta.pdf", "Alpha.pdf", "config.xml"), bySizeDesc.map { it.name })

        val bySizeAsc = DocumentQueryProcessor.filterAndSort(
            documents = docs,
            query = "",
            sortOption = SortOption.SIZE_ASC
        )
        assertEquals(listOf("config.xml", "Alpha.pdf", "zeta.pdf", "beta.docx"), bySizeAsc.map { it.name })

        // Sort by Date DESC & ASC
        val byDateDesc = DocumentQueryProcessor.filterAndSort(
            documents = docs,
            query = "",
            sortOption = SortOption.DATE_DESC
        )
        assertEquals(listOf("config.xml", "Alpha.pdf", "beta.docx", "zeta.pdf"), byDateDesc.map { it.name })

        val byDateAsc = DocumentQueryProcessor.filterAndSort(
            documents = docs,
            query = "",
            sortOption = SortOption.DATE_ASC
        )
        assertEquals(listOf("zeta.pdf", "beta.docx", "Alpha.pdf", "config.xml"), byDateAsc.map { it.name })

        // Search by dot-folder path & parent folder case-insensitively
        val dotSearch = DocumentQueryProcessor.groupByType(
            documents = docs,
            query = "  VAULT ",
            sortOption = SortOption.DATE_DESC
        )
        assertEquals(listOf("Alpha.pdf"), dotSearch[DocType.PDF]?.map { it.name })
        assertEquals(emptyList<DocumentItem>(), dotSearch[DocType.DOC])

        val folderSearch = DocumentQueryProcessor.filterAndSort(
            documents = docs,
            query = "workdocs",
            sortOption = SortOption.NAME_ASC
        )
        assertEquals(listOf("beta.docx"), folderSearch.map { it.name })

        // Clearing search restores full grouped list
        val restored = DocumentQueryProcessor.groupByType(
            documents = docs,
            query = "",
            sortOption = SortOption.DATE_DESC
        )
        assertEquals(2, restored[DocType.PDF]?.size)
        assertEquals(1, restored[DocType.DOC]?.size)
        assertEquals(1, restored[DocType.XML]?.size)

        // Filtering by visibleTypes hides toggled-off document formats
        val hiddenPdfAndXml = DocumentQueryProcessor.groupByType(
            documents = docs,
            query = "",
            sortOption = SortOption.DATE_DESC,
            visibleTypes = setOf(DocType.DOC, DocType.PPT, DocType.TEXT)
        )
        assertEquals(emptyList<DocumentItem>(), hiddenPdfAndXml[DocType.PDF])
        assertEquals(emptyList<DocumentItem>(), hiddenPdfAndXml[DocType.XML])
        assertEquals(listOf("beta.docx"), hiddenPdfAndXml[DocType.DOC]?.map { it.name })
    }

    @Test
    fun queryProcessor_resolvesHomePillsForPopulatedCategories() {
        val allEnabled = DocType.entries.toSet()

        // 1 populated category -> shows 1 pill
        val oneCategory = listOf(fakeDoc("a.pdf", DocType.PDF, 100, 1000L))
        assertEquals(listOf(DocType.PDF), DocumentQueryProcessor.resolveHomePillTypes(oneCategory, allEnabled))

        // 2 populated categories -> shows 2 pills
        val twoCategories = listOf(
            fakeDoc("a.pdf", DocType.PDF, 100, 1000L),
            fakeDoc("b.docx", DocType.DOC, 100, 1000L)
        )
        assertEquals(listOf(DocType.PDF, DocType.DOC), DocumentQueryProcessor.resolveHomePillTypes(twoCategories, allEnabled))

        // 3 populated categories -> shows 3 pills
        val threeCategories = twoCategories + fakeDoc("c.xml", DocType.XML, 100, 1000L)
        assertEquals(
            listOf(DocType.PDF, DocType.DOC, DocType.XML),
            DocumentQueryProcessor.resolveHomePillTypes(threeCategories, allEnabled)
        )

        // Settings hides DOC -> excludes DOC pill
        assertEquals(
            listOf(DocType.PDF, DocType.XML),
            DocumentQueryProcessor.resolveHomePillTypes(threeCategories, setOf(DocType.PDF, DocType.XML, DocType.PPT, DocType.TEXT))
        )

        // Empty document list -> falls back to all enabled pills
        assertEquals(DocType.entries, DocumentQueryProcessor.resolveHomePillTypes(emptyList(), allEnabled))

        // All disabled in Settings -> empty list
        assertEquals(emptyList<DocType>(), DocumentQueryProcessor.resolveHomePillTypes(threeCategories, emptySet()))
    }

    @Test
    fun queryProcessor_cyclesThemeModesInAutoDarkLightOrder() {
        assertEquals(
            com.example.model.ThemeMode.DARK,
            DocumentQueryProcessor.nextThemeMode(com.example.model.ThemeMode.SYSTEM)
        )
        assertEquals(
            com.example.model.ThemeMode.LIGHT,
            DocumentQueryProcessor.nextThemeMode(com.example.model.ThemeMode.DARK)
        )
        assertEquals(
            com.example.model.ThemeMode.SYSTEM,
            DocumentQueryProcessor.nextThemeMode(com.example.model.ThemeMode.LIGHT)
        )
    }

    @Test
    fun backgroundLifecyclePolicy_enforcesOneMinuteTimeoutAndOnDemandService() {
        assertEquals(60_000L, BackgroundLifecyclePolicy.BACKGROUND_TIMEOUT_MS)

        // Lite service only starts when active work > 0, not already running, and allowed
        assertFalse(BackgroundLifecyclePolicy.shouldStartLiteService(activeWorkCount = 0, isServiceRunning = false, canStartService = true))
        assertTrue(BackgroundLifecyclePolicy.shouldStartLiteService(activeWorkCount = 1, isServiceRunning = false, canStartService = true))
        assertFalse(BackgroundLifecyclePolicy.shouldStartLiteService(activeWorkCount = 1, isServiceRunning = true, canStartService = true))
        assertFalse(BackgroundLifecyclePolicy.shouldStartLiteService(activeWorkCount = 1, isServiceRunning = false, canStartService = false))

        // Stops immediately once active work drops to 0
        assertTrue(BackgroundLifecyclePolicy.shouldStopLiteService(activeWorkCount = 0, isServiceRunning = true))
        assertFalse(BackgroundLifecyclePolicy.shouldStopLiteService(activeWorkCount = 1, isServiceRunning = true))

        // Auto-closes only when in background for at least 1 minute
        assertFalse(BackgroundLifecyclePolicy.shouldAutoCloseBackgroundApp(startedActivityCount = 1, elapsedInBackgroundMs = 60_000L))
        assertFalse(BackgroundLifecyclePolicy.shouldAutoCloseBackgroundApp(startedActivityCount = 0, elapsedInBackgroundMs = 59_999L))
        assertTrue(BackgroundLifecyclePolicy.shouldAutoCloseBackgroundApp(startedActivityCount = 0, elapsedInBackgroundMs = 60_000L))
    }

    @Test
    fun ooxmlParser_extractsAlignmentImagesChartsPageBreaksAndTables() {
        val fakePng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0xF3.toByte(), 0xFF.toByte(), 0x61
        )
        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:styleId="CoverTitle">
                <w:name w:val="Title"/>
                <w:pPr><w:jc w:val="center"/></w:pPr>
                <w:rPr><w:b/><w:sz w:val="36"/></w:rPr>
              </w:style>
            </w:styles>
        """.trimIndent()
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rIdImg1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/logo.png"/>
              <Relationship Id="rIdChart1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="charts/chart1.xml"/>
            </Relationships>
        """.trimIndent()
        val chartXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart">
              <c:chart>
                <c:title><c:tx><c:rich><a:p><a:r><a:t>Urban Growth</a:t></a:r></a:p></c:rich></c:tx></c:title>
                <c:plotArea>
                  <c:barChart>
                    <c:barDir c:val="col"/>
                    <c:ser>
                      <c:tx><c:strRef><c:strCache><c:pt idx="0"><c:v>Population</c:v></c:pt></c:strCache></c:strRef></c:tx>
                      <c:cat><c:strRef><c:strCache><c:pt idx="0"><c:v>2025</c:v></c:pt><c:pt idx="1"><c:v>2026</c:v></c:pt></c:strCache></c:strRef></c:cat>
                      <c:val><c:numRef><c:numCache><c:pt idx="0"><c:v>42</c:v></c:pt><c:pt idx="1"><c:v>68</c:v></c:pt></c:numCache></c:numRef></c:val>
                    </c:ser>
                  </c:barChart>
                </c:plotArea>
              </c:chart>
            </c:chartSpace>
        """.trimIndent()
        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="CoverTitle"/></w:pPr>
                  <w:r><w:t>SMART TOWN PLANNING</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:jc w:val="center"/></w:pPr>
                  <w:r><w:drawing><wp:inline><wp:extent cx="1270000" cy="1270000"/><a:graphic><a:graphicData><a:blip r:embed="rIdImg1"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>
                </w:p>
                <w:p><w:r><w:br w:type="page"/></w:r></w:p>
                <w:p>
                  <w:r><w:drawing><wp:inline><a:graphic><a:graphicData><c:chart r:id="rIdChart1"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>
                </w:p>
                <w:tbl>
                  <w:tblPr>
                    <w:tblBorders>
                      <w:top w:val="none"/><w:bottom w:val="none"/><w:left w:val="none"/><w:right w:val="none"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Supervisor</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Head of Dept</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                fun put(name: String, content: ByteArray) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content)
                    zos.closeEntry()
                }
                put("word/styles.xml", stylesXml.toByteArray())
                put("word/_rels/document.xml.rels", relsXml.toByteArray())
                put("word/media/logo.png", fakePng)
                put("word/charts/chart1.xml", chartXml.toByteArray())
                put("word/document.xml", docXml.toByteArray())
            }
            baos.toByteArray()
        }

        val blocks = OoxmlDocumentParser.parseDocxBytes(zipBytes)
        val titleBlock = blocks.firstOrNull { it.text == "SMART TOWN PLANNING" }
        assertNotNull(titleBlock)
        assertEquals(DocAlignment.CENTER, titleBlock!!.alignment)
        assertEquals(18f, titleBlock.fontSizePt)
        assertTrue(titleBlock.isBold)

        val imageBlock = blocks.firstOrNull { it.image != null }
        assertNotNull(imageBlock)
        val img = imageBlock!!.image!!
        assertTrue(img.bytes.contentEquals(fakePng))
        assertEquals(100f, img.widthPt!!, 0.5f)

        assertTrue(blocks.any { it.isPageBreak })

        val chartBlock = blocks.firstOrNull { it.chart != null }
        assertNotNull(chartBlock)
        val parsedChart = chartBlock!!.chart!!
        assertEquals("Urban Growth", parsedChart.title)
        assertEquals(OfficeChartType.COLUMN, parsedChart.type)
        assertEquals(listOf("2025", "2026"), parsedChart.categories)
        assertEquals(listOf(42.0, 68.0), parsedChart.series.first().values)

        val tableBlock = blocks.firstOrNull { it.tableRows.isNotEmpty() }
        assertNotNull(tableBlock)
        assertFalse(tableBlock!!.tableHasBorders)
        assertEquals(2, tableBlock.tableRows.first().cells.size)
    }

    @Test
    fun officeXmlParser_parsesFlatOpcWordAndEmbeddedXmlImages() {
        val fakePng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x08,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x4B, 0x6D, 0x29
        )
        val b64 = Base64.getEncoder().encodeToString(fakePng)

        val flatOpcXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <?mso-application progid="Word.Document"?>
            <pkg:package xmlns:pkg="http://schemas.microsoft.com/office/2006/xmlPackage">
              <pkg:part pkg:name="/word/_rels/document.xml.rels" pkg:contentType="application/vnd.openxmlformats-package.relationships+xml">
                <pkg:xmlData>
                  <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Target="media/fig1.png"/>
                  </Relationships>
                </pkg:xmlData>
              </pkg:part>
              <pkg:part pkg:name="/word/media/fig1.png" pkg:contentType="image/png">
                <pkg:binaryData>$b64</pkg:binaryData>
              </pkg:part>
              <pkg:part pkg:name="/word/document.xml" pkg:contentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml">
                <pkg:xmlData>
                  <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                              xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <w:body>
                      <w:p><w:r><w:t>XML Report Heading</w:t></w:r></w:p>
                      <w:p><w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r></w:p>
                    </w:body>
                  </w:document>
                </pkg:xmlData>
              </pkg:part>
            </pkg:package>
        """.trimIndent()

        val blocks = OfficeXmlFormatParser.tryParseOfficeOrMediaXml(flatOpcXml)
        assertNotNull(blocks)
        assertTrue(blocks!!.any { it.text == "XML Report Heading" })
        assertTrue(blocks.any { it.image?.bytes?.contentEquals(fakePng) == true })

        val genericXmlWithImage = "<report><preview src=\"data:image/png;base64,$b64\"/></report>"
        val media = OfficeXmlFormatParser.extractEmbeddedMediaBlocks(genericXmlWithImage)
        assertEquals(1, media.size)
        assertTrue(media.first().image!!.bytes.contentEquals(fakePng))
    }
}
