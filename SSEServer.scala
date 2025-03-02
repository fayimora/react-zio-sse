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

  val MaxEvents = 1000
  val program =
    for
      recentEventsRef <- Ref.make(List.empty[Payload])
      counterRef      <- Ref.make(0)
      hub             <- Hub.bounded[Payload](MaxEvents)
      // Start the event generator in the background
      _ <- ZStream
        .tick(5.second)
        .zipWith(ZStream.range(1, MaxEvents))((_, n) =>
          Payload(
            id = n,
            message = s"Update #$n",
            timestamp = LocalDateTime.now().toString
          )
        )
        .tap(payload =>
          recentEventsRef.update(events =>
            (payload :: events).take(3)
          ) *> counterRef.update(_ + 1)
        )
        .foreach(payload => hub.publish(payload))
        .forever
        .fork

      sseRoute = Method.GET / "api" / "events" -> handler { (req: Request) =>
        for {
          // Subscribe to the hub to get a stream of events
          dequeue <- hub.subscribe
          stream = ZStream.fromQueueWithShutdown(dequeue)

          // Convert payloads to SSE events
          sseEvents = stream.map(payload =>
            ServerSentEvent(
              data = payload.toJson,
              eventType = Some("UPDATE"),
              id = Some(payload.id.toString),
              retry = Some(500)
            )
          )
        } yield Response(
          status = Status.Ok,
          headers = Headers(
            Header.ContentType(MediaType.text.`event-stream`),
            Header.CacheControl.NoCache
          ),
          body = Body.fromStream(sseEvents.map(_.encode))
        )
      }

      // Define the latest data route
      latestDataRoute = Method.GET / "api" / "latest" -> handler {
        (req: Request) =>
          for {
            recentEvents <- recentEventsRef.get
            response = Response
              .json(recentEvents.toJson)
              .addHeader(Header.AccessControlAllowOrigin.All)
          } yield response
      }

      countRoute = Method.GET / "api" / "count" -> handler { (req: Request) =>
        for {
          counter <- counterRef.get
          response = Response(
            status = Status.Ok,
            body = Body.fromString(counter.toString)
          )
        } yield response
      }

      rootRoute = Method.GET / Root -> handler(Response(status = Status.Ok))

      httpApp = Routes(
        sseRoute,
        latestDataRoute,
        countRoute,
        rootRoute
      ) @@ Middleware.cors
      _ <- Server
        .serve(httpApp)
        .provide(Server.defaultWith(_.port(8090)), Scope.default)
    yield ()

  override def run = program
