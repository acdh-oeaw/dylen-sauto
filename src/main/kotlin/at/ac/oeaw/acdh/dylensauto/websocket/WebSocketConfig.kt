package at.ac.oeaw.acdh.dylensauto.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(protected val sautoWebSocketHandler: SautoWebSocketHandler) : WebSocketConfigurer {
    //    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
//        registry.addHandler(sautoWebSocketHandler, "/app")
//            .setAllowedOrigins(
//                "http://localhost:8080",
//                "http://127.0.0.1:8080",
//                "https://dylen-tool.acdh-dev.oeaw.ac.at/",
//                "https://dylen-tool.acdh.oeaw.ac.at/"
//
//            )
//    }
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        //Ignore
        //Since we have enough sessions, we don't accept anymore. If for some reason, you want to activate, remove this
        //method and remove the comments from the top method
    }
}