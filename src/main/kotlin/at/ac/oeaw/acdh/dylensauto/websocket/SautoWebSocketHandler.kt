package at.ac.oeaw.acdh.dylensauto.websocket

import at.ac.oeaw.acdh.dylensauto.dao.SessionRepository
import at.ac.oeaw.acdh.dylensauto.entity.Session
import at.ac.oeaw.acdh.dylensauto.entity.dto.*
import at.ac.oeaw.acdh.dylensauto.exceptions.JSONException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Component
class SautoWebSocketHandler(
    val mapper: ObjectMapper,
    val sessionRepository: SessionRepository
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(webSession: WebSocketSession) {
        val session = Session(
            webSession.id,
            Date(),
            Date(),
            0L,
            ArrayList(),
            ArrayList(),
            ArrayList(),
            ArrayList(),
            ArrayList(),
            ArrayList(),
            ArrayList(),
            ArrayList(),
            0.0,
            HashMap(),
            0,

        )
        sessionRepository.save(session)

        logger.info("Connected new session with id: ${session.id}");
    }

    override fun afterConnectionClosed(webSession: WebSocketSession, status: CloseStatus) {
        val sessionOptional: Optional<Session> = sessionRepository.findById(webSession.id)
        val session: Session = sessionOptional.get()
        //end time is the last mouse movement and not the closing of the connection
        val end = Date(session.mousePositions.maxByOrNull { it.timestamp }!!.timestamp)
        session.endTime = end
        val diffInMillies: Long = Math.abs(end.time - session.startTime.time)
        session.duration = TimeUnit.MILLISECONDS.toSeconds(diffInMillies)
        session.code = status.code //codes defined here: https://www.rfc-editor.org/rfc/rfc6455#section-7.4.1
        sessionRepository.save(session)

        logger.info("Session ${webSession.id} closed with code: ${status.code}")
    }

    override fun handleTextMessage(webSession: WebSocketSession, textMessage: TextMessage) {
        val message: Message;
        try {
            message = mapper.readValue(textMessage.payload, Message::class.java)
        } catch (e: JsonProcessingException) {
            handleTransportError(webSession, e)
            return
        }

        val sessionOptional = sessionRepository.findById(webSession.id)
        val session = sessionOptional.get() //must be present

        try {
            session.add(message)
        } catch (e: JSONException) {
            handleTransportError(webSession, e)
        }
        sessionRepository.save(session)
    }


    override fun handleTransportError(webSession: WebSocketSession, exception: Throwable) {
        logger.error("Error occurred on websocket from sender: ${webSession.id} : ", exception)
    }
}