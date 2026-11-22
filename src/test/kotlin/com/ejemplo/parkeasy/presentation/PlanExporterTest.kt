package com.ejemplo.parkeasy.presentation

import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanDocument
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.presentation.plan.PlanExporter
import com.ejemplo.parkeasy.presentation.plan.PlanUiState
import com.ejemplo.parkeasy.presentation.plan.SpotRuntime
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanExporterTest {

    private fun state(): PlanUiState {
        val items = listOf(
            PlanItem("z", ItemType.COVERED_ZONE, Box(1.0, 1.0, 14.0, 10.0)),
            PlanItem("r", ItemType.ROAD, PathGeometry(PathKind.POLYLINE, listOf(Pt(1.0, 14.0), Pt(30.0, 14.0))), strokeWidth = 4.0),
            PlanItem("a", ItemType.SPOT, Box(2.0, 2.0, 2.5, 5.0), label = "A-01", spotId = 1, spotType = SpotType.CAR),
            PlanItem("b", ItemType.SPOT, Box(5.0, 2.0, 2.5, 5.0), label = "A-02", spotId = 2, spotType = SpotType.CAR),
            PlanItem("c", ItemType.SPOT, Box(8.0, 2.0, 2.5, 5.0), label = "A-03", spotId = 3, spotType = SpotType.CAR),
            PlanItem("e", ItemType.ENTRANCE, Box(1.0, 18.0, 4.0, 1.0), label = "Entrada"),
        )
        return PlanUiState(
            loading = false,
            doc = PlanDocument(1, 32.0, 22.0, items),
            runtime = mapOf(
                1L to SpotRuntime(SpotStatus.FREE, null, null),
                2L to SpotRuntime(SpotStatus.OCCUPIED, "ABC123", 10),
                3L to SpotRuntime(SpotStatus.BLOCKED, null, null),
            ),
        )
    }

    @Test
    fun `el PNG tiene el tamaño del plano y contenido dibujado`() {
        val bytes = PlanExporter.renderPng(state(), "Piso 1", operation = true)
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(32 * 40 + 96, image.width)
        assertEquals(22 * 40 + 112, image.height)

        val colors = HashSet<Int>()
        for (x in 0 until image.width step 7) for (y in 0 until image.height step 7) colors += image.getRGB(x, y)
        assertTrue(colors.size > 6, "el plano no puede ser una imagen en blanco (${colors.size} colores)")
    }

    @Test
    fun `en modo operación cada estado se pinta distinto`() {
        val image = ImageIO.read(ByteArrayInputStream(PlanExporter.renderPng(state(), "Piso 1", operation = true)))
        // Centro de cada puesto en píxeles: margen + metros * 40
        fun pixel(xm: Double, ym: Double) = image.getRGB((48 + xm * 40).toInt(), (64 + ym * 40).toInt())
        val free = pixel(3.25, 6.5)
        val occupied = pixel(6.25, 6.5)
        val blocked = pixel(9.25, 6.5)
        assertTrue(setOf(free, occupied, blocked).size == 3, "libre, ocupado y bloqueado deben verse distintos")
    }

    @Test
    fun `el PDF tiene estructura válida y la tabla de referencias apunta a cada objeto`() {
        val pdf = PlanExporter.pdfFromPng(PlanExporter.renderPng(state(), "Piso 1", operation = false))
        val text = String(pdf, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))

        val startxref = Regex("startxref\\n(\\d+)").find(text)!!.groupValues[1].toInt()
        assertTrue(text.substring(startxref).startsWith("xref"))
        val entries = Regex("(\\d{10}) 00000 n ").findAll(text.substring(startxref)).map { it.groupValues[1].toInt() }.toList()
        assertEquals(5, entries.size)
        entries.forEachIndexed { i, offset ->
            assertTrue(text.substring(offset).startsWith("${i + 1} 0 obj"), "el objeto ${i + 1} no está en su posición")
        }
        assertTrue("/MediaBox [0 0 842.00 595.00]" in text, "el plano es más ancho que alto: página horizontal")
    }

    @Test
    fun `exportar escribe los archivos`() {
        val dir = Files.createTempDirectory("parkeasy-export")
        PlanExporter.exportPng(state(), "Piso 1", true, dir.resolve("plano.png"))
        PlanExporter.exportPdf(state(), "Piso 1", true, dir.resolve("plano.pdf"))
        assertTrue(Files.size(dir.resolve("plano.png")) > 1000)
        assertTrue(Files.size(dir.resolve("plano.pdf")) > 1000)
        // copia para revisar a ojo
        System.getenv("PARKEASY_EXPORT_PREVIEW")?.let { Files.copy(dir.resolve("plano.png"), java.nio.file.Paths.get(it), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    }
}
