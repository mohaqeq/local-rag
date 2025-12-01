package localrag.clients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import localrag.Config
import localrag.models.ChatRequest
import localrag.models.ChatResponse
import localrag.models.EmbedRequest
import localrag.models.EmbedResponse
import localrag.models.Message
import org.slf4j.LoggerFactory

class OllamaClient(private val config: Config) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 300000  // 5 minutes for LLM requests
            connectTimeoutMillis = 30000   // 30 seconds to connect
            socketTimeoutMillis = 300000   // 5 minutes for socket read/write
        }
    }

    suspend fun generateEmbedding(text: String): List<Double> {
        return try {
            val response = client.post("${config.ollamaBaseUrl}/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        EmbedRequest(
                            model = config.textEmbeddingModel,
                            prompt = text
                        )
                    )
                )
            }
            val responseBody = response.bodyAsText()
            val embedResponse = json.decodeFromString<EmbedResponse>(responseBody)
            embedResponse.embedding
        } catch (e: Exception) {
            logger.error("Error generating embedding", e)
            throw e
        }
    }

    suspend fun chat(messages: List<Message>): String {
        return try {
            val response = client.post("${config.ollamaBaseUrl}/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        ChatRequest(
                            model = config.llmModel,
                            messages = messages,
                            stream = false
                        )
                    )
                )
            }

            // Read response body and parse NDJSON
            val responseBody = response.bodyAsText()
            val lines = responseBody.trim().split("\n").filter { it.isNotBlank() }

            // Accumulate content from all response lines
            val contentBuilder = StringBuilder()
            for (line in lines) {
                try {
                    val chatResponse = json.decodeFromString<ChatResponse>(line)
                    contentBuilder.append(chatResponse.message.content)

                    if (chatResponse.done) {
                        break
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse response line: $line", e)
                }
            }

            contentBuilder.toString()
        } catch (e: Exception) {
            logger.error("Error in chat", e)
            throw e
        }
    }

    fun close() {
        client.close()
    }
}
