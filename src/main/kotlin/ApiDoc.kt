package localrag

import io.ktor.server.application.Application
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

fun Application.configureApiDoc(){
    routing {
        openAPI(path = "openapi", swaggerFile = "openapi/generated.json")
    }
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/generated.json")
    }
}
