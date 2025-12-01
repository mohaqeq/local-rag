package localrag.clients

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import localrag.Config
import localrag.models.Document
import org.slf4j.LoggerFactory

class PgVectorClient(private val config: Config) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val dataSource: HikariDataSource
    private val json = Json { ignoreUnknownKeys = true }

    init {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${config.dbHost}:${config.dbPort}/${config.dbName}"
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
        }
        dataSource = HikariDataSource(hikariConfig)

        initialize()
    }

    private fun initialize() {
        try {
            dataSource.connection.use { conn ->
                // Enable pgvector extension
                conn.createStatement().use { stmt ->
                    stmt.execute("CREATE EXTENSION IF NOT EXISTS vector")
                    logger.info("pgvector extension enabled")
                }

                // Create documents table with vector column
                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ${config.dbTableName} (
                            id TEXT PRIMARY KEY,
                            text TEXT NOT NULL,
                            metadata JSONB,
                            embedding vector(1024)
                        )
                    """.trimIndent())
                    logger.info("Table '${config.dbTableName}' created or already exists")
                }

                // Create index for faster similarity search
                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS ${config.dbTableName}_embedding_idx
                        ON ${config.dbTableName}
                        USING ivfflat (embedding vector_cosine_ops)
                        WITH (lists = 100)
                    """.trimIndent())
                    logger.info("Vector index created or already exists")
                }
            }
        } catch (e: Exception) {
            logger.error("Error initializing database", e)
            throw e
        }
    }

    fun addDocuments(documents: List<Document>, embeddings: List<List<Double>>) {
        if (documents.size != embeddings.size) {
            throw IllegalArgumentException("Number of documents must match number of embeddings")
        }

        try {
            dataSource.connection.use { conn ->
                conn.autoCommit = false

                val sql = """
                    INSERT INTO ${config.dbTableName} (id, text, metadata, embedding)
                    VALUES (?, ?, ?::jsonb, ?::vector)
                    ON CONFLICT (id) DO UPDATE SET
                        text = EXCLUDED.text,
                        metadata = EXCLUDED.metadata,
                        embedding = EXCLUDED.embedding
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    documents.forEachIndexed { index, doc ->
                        stmt.setString(1, doc.id)
                        stmt.setString(2, doc.text)
                        stmt.setString(3, json.encodeToString(doc.metadata))
                        stmt.setString(4, embeddings[index].toString())
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }

                conn.commit()
                logger.info("Added ${documents.size} documents to PostgreSQL")
            }
        } catch (e: Exception) {
            logger.error("Error adding documents to PostgreSQL", e)
            throw e
        }
    }

    fun query(queryEmbedding: List<Double>, nResults: Int = 5): List<Document> {
        return try {
            val results = mutableListOf<Document>()

            dataSource.connection.use { conn ->
                val sql = """
                    SELECT id, text, metadata, embedding <-> ?::vector AS distance
                    FROM ${config.dbTableName}
                    ORDER BY distance
                    LIMIT ?
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, queryEmbedding.toString())
                    stmt.setInt(2, nResults)

                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        val metadata = rs.getString("metadata")?.let {
                            json.decodeFromString<Map<String, String>>(it)
                        } ?: emptyMap()

                        results.add(
                            Document(
                                id = rs.getString("id"),
                                text = rs.getString("text"),
                                metadata = metadata
                            )
                        )
                    }
                }
            }

            results
        } catch (e: Exception) {
            logger.error("Error querying PostgreSQL", e)
            emptyList()
        }
    }

    fun close() {
        dataSource.close()
        logger.info("Database connection pool closed")
    }
}
