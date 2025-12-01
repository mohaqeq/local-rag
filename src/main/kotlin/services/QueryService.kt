package localrag.services

import localrag.clients.OllamaClient
import localrag.clients.PgVectorClient
import localrag.models.Message
import org.slf4j.LoggerFactory

class QueryService(
    private val ollamaClient: OllamaClient,
    private val pgVectorClient: PgVectorClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun query(userQuery: String): String? {
        return try {
            logger.info("Processing query: $userQuery")

            // Generate multiple query variations
            val queryVariations = generateQueryVariations(userQuery)
            logger.info("Generated ${queryVariations.size} query variations")

            // Get embeddings for all variations and retrieve documents
            val allDocuments = mutableSetOf<String>()
            for (variation in queryVariations) {
                val embedding = ollamaClient.generateEmbedding(variation)
                val documents = pgVectorClient.query(embedding, nResults = 3)
                allDocuments.addAll(documents.map { it.text })
            }

            if (allDocuments.isEmpty()) {
                logger.warn("No relevant documents found for query")
                return "I couldn't find any relevant information in the knowledge base to answer your question."
            }

            // Combine context
            val context = allDocuments.joinToString("\n\n")
            logger.info("Retrieved ${allDocuments.size} unique document chunks")

            // Generate answer based on context
            val answer = generateAnswer(context, userQuery)

            logger.info("Successfully generated answer: $answer")
            answer
        } catch (e: Exception) {
            logger.error("Error processing query", e)
            null
        }
    }

    private suspend fun generateQueryVariations(originalQuery: String): List<String> {
        val prompt = """You are an AI language model assistant. Your task is to generate five
different versions of the given user question to retrieve relevant documents from
a vector database. By generating multiple perspectives on the user question, your
goal is to help the user overcome some of the limitations of the distance-based
similarity search. Provide these alternative questions separated by newlines.
Original question: $originalQuery"""

        val messages = listOf(
            Message(role = "user", content = prompt)
        )

        return try {
            val response = ollamaClient.chat(messages)
            val variations = response.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("Original question:") }
                .take(5)

            // Always include the original query
            listOf(originalQuery) + variations
        } catch (e: Exception) {
            logger.error("Error generating query variations, using original query only", e)
            listOf(originalQuery)
        }
    }

    private suspend fun generateAnswer(context: String, question: String): String {
        val prompt = """Answer the question based ONLY on the following context:

$context

Question: $question"""

        val messages = listOf(
            Message(role = "user", content = prompt)
        )

        return ollamaClient.chat(messages)
    }
}
