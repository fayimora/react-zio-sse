
FROM virtuslab/scala-cli:latest as builder
WORKDIR /app
COPY SSEServer.scala .
RUN scala-cli --power package --assembly SSEServer.scala -o sse-server --native-image 

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/sse-server .
EXPOSE 8090
CMD ["./sse-server"]
