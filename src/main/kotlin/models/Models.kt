package localrag.models

import kotlinx.serialization.Serializable

@Serializable
data class Document(
    val id: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class EmbedRequest(
    val model: String,
    val prompt: String
)

@Serializable
data class EmbedResponse(
    val embedding: List<Double>
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val message: Message,
    val done: Boolean
)

@Serializable
data class QueryRequest(
    val query: String
)

@Serializable
data class QueryResponse(
    val message: String
)

@Serializable
data class EmbedFileResponse(
    val message: String
)

@Serializable
data class ErrorResponse(
    val error: String
)
