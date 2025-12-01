package localrag

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.http.content.file
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.io.files.SystemFileSystem
import localrag.clients.PgVectorClient
import localrag.clients.OllamaClient
import localrag.clients.PDFProcessor
import localrag.models.EmbedFileResponse
import localrag.models.ErrorResponse
import localrag.models.QueryRequest
import localrag.models.QueryResponse
import localrag.services.EmbeddingService
import localrag.services.QueryService
import org.slf4j.LoggerFactory
import java.io.File

fun Application.configureRouting(config: Config) {
    val logger = LoggerFactory.getLogger("Routing")
    val ollamaClient = OllamaClient(config)
    val pgVectorClient = PgVectorClient(config)
    val pdfProcessor = PDFProcessor(config)
    val embeddingService = EmbeddingService(ollamaClient, pgVectorClient, pdfProcessor)
    val queryService = QueryService(ollamaClient, pgVectorClient)

    routing {
        get("/") {
            call.respondText("Local RAG Application is running!", ContentType.Text.Plain)
        }

        get("/health") {
            call.respond(mapOf("status" to "healthy"))
        }

        post("/embed") {
            try {
                val multipart = call.receiveMultipart()
                var tempFile: File? = null
                var originalFilename: String? = null

                // Process file immediately while stream is available
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            originalFilename = part.originalFileName ?: ""

                            if (originalFilename.isNotEmpty() && embeddingService.validatePDFFile(originalFilename)) {
                                // Save file immediately while stream is available
                                val timestamp = System.currentTimeMillis()
                                val safeFilename = originalFilename.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                tempFile = File(config.tempFolder, "${timestamp}_$safeFilename")

                                part.provider().copyAndClose(tempFile.writeChannel())

                                logger.info("Saved file: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                            }
                            part.dispose()
                        }
                        else -> part.dispose()
                    }
                }

                // Validate we got a file
                if (tempFile == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("No valid PDF file provided"))
                    return@post
                }

                if (originalFilename.isNullOrEmpty()) {
                    tempFile.delete()
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("No selected file"))
                    return@post
                }

                // Process the file
                val success = embeddingService.embedFile(tempFile!!, originalFilename!!)

                // Clean up temp file
                tempFile.delete()

                if (success) {
                    call.respond(HttpStatusCode.OK, EmbedFileResponse("File embedded successfully"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("File embedded unsuccessfully"))
                }
            } catch (e: Exception) {
                logger.error("Error in /embed endpoint", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error processing file: ${e.message}"))
            }
        }

        post("/query") {
            try {
                val request = call.receive<QueryRequest>()

                if (request.query.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query cannot be empty"))
                    return@post
                }

                val response = queryService.query(request.query)

                if (response != null) {
                    call.respond(HttpStatusCode.OK, QueryResponse(response))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Something went wrong"))
                }
            } catch (e: Exception) {
                logger.error("Error in /query endpoint", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error processing query: ${e.message}"))
            }
        }
    }

    // Graceful shutdown
    monitor.subscribe(ApplicationStopped) {
        logger.info("Application stopping, cleaning up resources...")
        ollamaClient.close()
        pgVectorClient.close()
    }
}
