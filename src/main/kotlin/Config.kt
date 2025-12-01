package localrag

import io.ktor.server.config.ApplicationConfig

class Config(config: ApplicationConfig) {
    val tempFolder: String = config.property("app.tempFolder").getString()
    val llmModel: String = config.property("app.llmModel").getString()
    val textEmbeddingModel: String = config.property("app.textEmbeddingModel").getString()
    val ollamaBaseUrl: String = config.property("app.ollamaBaseUrl").getString()
    val chunkSize: Int = config.property("app.chunkSize").getString().toInt()
    val chunkOverlap: Int = config.property("app.chunkOverlap").getString().toInt()

    // PostgreSQL settings
    val dbHost: String = config.property("app.db.host").getString()
    val dbPort: Int = config.property("app.db.port").getString().toInt()
    val dbName: String = config.property("app.db.name").getString()
    val dbUser: String = config.property("app.db.user").getString()
    val dbPassword: String = config.property("app.db.password").getString()
    val dbTableName: String = config.property("app.db.tableName").getString()
}
