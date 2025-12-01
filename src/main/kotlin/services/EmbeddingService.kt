package localrag.services


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import localrag.clients.PgVectorClient
import localrag.clients.OllamaClient
import localrag.clients.PDFProcessor
import localrag.models.Document
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

class EmbeddingService(
    private val ollamaClient: OllamaClient,
    private val pgVectorClient: PgVectorClient,
    private val pdfProcessor: PDFProcessor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun embedFile(file: File, filename: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logger.info("Processing file: $filename")

                val text = pdfProcessor.extractText(file)
                logger.info("Extracted ${text.length} characters from PDF")

                val chunks = pdfProcessor.splitIntoChunks(text)

                val documents = chunks.mapIndexed { index, chunk ->
                    Document(
                        id = UUID.randomUUID().toString(),
                        text = chunk,
                        metadata = mapOf(
                            "source" to filename,
                            "chunk" to index.toString()
                        )
                    )
                }

                logger.info("Generating embeddings for ${documents.size} chunks...")
                val embeddings = documents.map { doc ->
                    ollamaClient.generateEmbedding(doc.text)
                }

                pgVectorClient.addDocuments(documents, embeddings)

                logger.info("Successfully embedded file: $filename")
                true
            } catch (e: Exception) {
                logger.error("Error embedding file: $filename", e)
                false
            }
        }
    }

    fun validatePDFFile(filename: String): Boolean {
        return filename.isNotEmpty() && filename.endsWith(".pdf", ignoreCase = true)
    }
}
