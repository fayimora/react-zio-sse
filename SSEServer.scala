//> using scala 3.5.0
//> using options -Wunused:all -Wvalue-discard -Wnonunit-statement

//> using dep dev.zio::zio-http::3.0.1
//> using dep dev.zio::zio-json::0.7.36
//> using dep dev.zio::zio-streams::2.1.16
//> using dep dev.zio::zio-schema::1.6.3
//> using dep dev.zio::zio::2.1.16

import java.time.LocalDateTime
import zio.*
import zio.http.*
import zio.http.codec.TextBinaryCodec.fromSchema
import zio.json.*
import zio.stream.*

object SseServer extends ZIOAppDefault:
  case class Payload(id: Int, message: String, timestamp: String)
      derives JsonCodec

  val eventStream =
    ZStream
      .tick(2.second)
      .zipWith(ZStream.range(1, 10))((_, n) =>
        Payload(n, s"Update #$n", LocalDateTime.now().toString)
      )

  val sseEvents =
    eventStream.map(data =>
      ServerSentEvent(
        data = data.toJson,
        eventType = Some("UPDATE"),
        retry = Some(500)
      )
    )

  val sseRoute =
    Method.GET / "api" / "events" -> handler(
      Response(
        status = Status.Ok,
        headers = Headers(Header.ContentType(MediaType.text.`event-stream`)),
        body = Body.fromStream(sseEvents.map(_.encode))
      )
    )

  val initialPayload =
    Payload(0, "Initial message", LocalDateTime.now().toString)

  val initialRoute = Method.GET / "api" / "initial" -> handler(
    Response(
      status = Status.Ok,
      headers = Headers(Header.ContentType(MediaType.application.json)),
      body = Body.from(initialPayload.toJson)
    )
  )

  val httpApp = Routes(sseRoute, initialRoute) @@ Middleware.cors

  override def run =
    Server
      .serve(httpApp)
      .provide(Server.defaultWith(_.port(8090)))
