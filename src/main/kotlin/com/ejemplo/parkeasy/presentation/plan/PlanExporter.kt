package com.ejemplo.parkeasy.presentation.plan

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import javax.imageio.ImageIO

/** Exporta el plano de un piso como imagen PNG o como PDF de una página. */
object PlanExporter {
    private const val MARGIN = 48
    private const val HEADER = 64
    private const val PIXELS_PER_METER = 40.0

    /** Dibuja [state] en una imagen: título arriba y el plano debajo, sin selección ni manijas. */
    fun renderPng(state: PlanUiState, title: String, operation: Boolean): ByteArray {
        val doc = requireNotNull(state.doc) { "No hay plano para exportar" }
        val width = (doc.width * PIXELS_PER_METER).toInt() + MARGIN * 2
        val height = (doc.height * PIXELS_PER_METER).toInt() + MARGIN + HEADER
        val bitmap = ImageBitmap(width, height)
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr, 8)

        val clean = state.copy(
            selection = emptySet(), preview = null, showDimensions = false, showGrid = true, highlighted = emptySet(),
            viewport = Viewport(PIXELS_PER_METER, MARGIN.toDouble(), HEADER.toDouble()),
        )
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Color.White, Offset.Zero, size)
            drawText(measurer, title, Offset(MARGIN.toFloat(), 14f), style = TextStyle(color = Color(0xFF1B1B1F), fontSize = 22.sp))
            drawPlan(measurer, clean, operation)
        }
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
        return checkNotNull(data) { "No se pudo codificar la imagen" }.bytes
    }

    fun exportPng(state: PlanUiState, title: String, operation: Boolean, target: Path) {
        Files.write(target, renderPng(state, title, operation))
    }

    fun exportPdf(state: PlanUiState, title: String, operation: Boolean, target: Path) {
        Files.write(target, pdfFromPng(renderPng(state, title, operation)))
    }

    /**
     * PDF de una página A4 con la imagen del plano centrada. Se escribe a mano (cabecera, catálogo,
     * página, imagen comprimida con Flate y tabla de referencias) para no sumar una librería.
     */
    internal fun pdfFromPng(png: ByteArray): ByteArray {
        val image = checkNotNull(ImageIO.read(ByteArrayInputStream(png))) { "Imagen ilegible" }
        val rgb = rgbBytes(image)
        val deflated = deflate(rgb)

        val landscape = image.width >= image.height
        val pageW = if (landscape) 842.0 else 595.0
        val pageH = if (landscape) 595.0 else 842.0
        val margin = 24.0
        val fit = minOf((pageW - 2 * margin) / image.width, (pageH - 2 * margin) / image.height)
        val w = image.width * fit
        val h = image.height * fit
        val x = (pageW - w) / 2
        val y = (pageH - h) / 2
        val content = "q ${num(w)} 0 0 ${num(h)} ${num(x)} ${num(y)} cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)

        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: () -> Unit) {
            offsets += out.size()
            write("${offsets.size} 0 obj\n")
            body()
            write("\nendobj\n")
        }

        write("%PDF-1.4\n")
        obj { write("<< /Type /Catalog /Pages 2 0 R >>") }
        obj { write("<< /Type /Pages /Kids [3 0 R] /Count 1 >>") }
        obj {
            write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(pageW)} ${num(pageH)}] ")
            write("/Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>")
        }
        obj {
            write("<< /Length ${content.size} >>\nstream\n")
            out.write(content)
            write("\nendstream")
        }
        obj {
            write("<< /Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} ")
            write("/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length ${deflated.size} >>\nstream\n")
            out.write(deflated)
            write("\nendstream")
        }
        val xref = out.size()
        write("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write("%010d 00000 n \n".format(it)) }
        write("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    private fun num(v: Double) = "%.2f".format(java.util.Locale.ROOT, v)

    private fun rgbBytes(image: BufferedImage): ByteArray {
        val out = ByteArray(image.width * image.height * 3)
        var i = 0
        for (yy in 0 until image.height) for (xx in 0 until image.width) {
            val p = image.getRGB(xx, yy)
            out[i++] = (p shr 16).toByte()
            out[i++] = (p shr 8).toByte()
            out[i++] = p.toByte()
        }
        return out
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }
}
