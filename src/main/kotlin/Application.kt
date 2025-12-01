package localrag

import io.ktor.server.application.Application
import java.io.File

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val config = Config(environment.config)

    File(config.tempFolder).mkdirs()

    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureApiDoc()
    configureRouting(config)
}
