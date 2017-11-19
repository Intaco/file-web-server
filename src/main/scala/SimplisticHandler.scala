import java.net._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp.Event
import akka.util.ByteString
import http.Method.{GET, HEAD, HttpMethod}
import http._
import org.apache.tika.Tika
import utils.ResponseUtils._

case object ConnectionFinish extends Event

case class WritingFile(filePath: String, position: Int, chunkSize: Int, fileSize: Long) extends Event

class SimplisticHandler(connection: ActorRef) extends Actor with ActorLogging {

  import akka.io.Tcp._

  def receive = {
    case Received(data) => {
      log.info(data.utf8String)
      val requestData = toRequestData(data.utf8String)
      val builder = new ResponseHeadersBuilder()
      if (requestData.requestPath.split("/").exists(_.startsWith(".."))) {
        val response = builder.responseLine(requestData.protocol, NotFound).build
        sender() ! Write(ByteString(response), ConnectionFinish)
      } else if (requestData.method != GET && requestData.method != HEAD) {
        val response = builder.responseLine(requestData.protocol, MethodNotAllowed).build
        sender() ! Write(ByteString(response), ConnectionFinish)
      } else {
        var filePathString = URLDecoder.decode(ROOT + requestData.requestPath, StandardCharsets.UTF_8.name())
        val pathProbe = Paths.get(filePathString) //TODO ???

        val isExistingDir = Files.exists(pathProbe) && Files.isDirectory(pathProbe)
        if (isExistingDir) filePathString += "index.html"

        if (Files.exists(Paths.get(filePathString))) {
          val filePath = Paths.get(filePathString)
          val contentType = Option(new Tika().detect(filePathString))
          val contentLength = Files.size(filePath)
          val response = builder.responseLine(requestData.protocol, OK)
            .headerLine("Server", "AkkaHttpFileServer")
            .headerLine("Date", currentTime())
            .headerLine("Connection", if (requestData.method == GET) "keep-alive" else "close")
            .headerLine("Content-Length", String.valueOf(contentLength))
            .headerLine("Content-Type", contentType.getOrElse("application/octet-stream"))
            .emptyLine()
            .build
          sender() ! Write(ByteString(response))
          if (requestData.method == GET)
            sender() ! WriteFile(filePathString, 0, 2048, WritingFile(filePathString, 2048, 2048, contentLength))
          else
            sender() ! Close
        } else if (isExistingDir) {
          sender() ! Write(ByteString(emptyResponseBody(requestData.protocol, Forbidden)), ConnectionFinish)
        } else {
          val response = builder.responseLine(requestData.protocol, NotFound)
            .headerLine("Server", "AkkaHttpFileServer")
            .headerLine("Date", currentTime())
            .headerLine("Connection", "close")
            .emptyLine()
            .build
          sender() ! Write(ByteString(response), ConnectionFinish)
        }
      }
    }

    case ConnectionFinish =>
      sender() ! Close
    case WritingFile(path, position, chunkSize, fileSize) =>
      sender() ! WriteFile(path, position, chunkSize, WritingFile(path, position + chunkSize, chunkSize, fileSize))
      if (fileSize < position + chunkSize) {
        sender() ! Close
      }

    case PeerClosed => context stop self
  }

  private def emptyResponseBody(protocol: String, status: HttpStatus): String = {
    new ResponseHeadersBuilder().responseLine(protocol, status)
      .headerLine("Server", "AkkaHttpFileServer")
      .headerLine("Date", currentTime())
      .headerLine("Connection", "close")
      .emptyLine()
      .build
  }
}