package localrag.clients

import localrag.Config
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import java.io.File

class PDFProcessor(private val config: Config) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun extractText(file: File): String {
        return try {
            Loader.loadPDF(file).use { document ->
                val stripper = PDFTextStripper()
                stripper.getText(document)
            }
        } catch (e: Exception) {
            logger.error("Error extracting text from PDF", e)
            throw e
        }
    }

    fun splitIntoChunks(
        text: String,
        chunkSize: Int = config.chunkSize,
        overlap: Int = config.chunkOverlap
    ): List<String> {
        val chunks = mutableListOf<String>()
        var startIndex = 0

        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + chunkSize, text.length)
            val chunk = text.substring(startIndex, endIndex)
            chunks.add(chunk)

            startIndex += chunkSize - overlap

            if (overlap >= chunkSize && startIndex < text.length) {
                startIndex = endIndex
            }
        }

        logger.info("Split text into ${chunks.size} chunks")
        return chunks
    }
}
