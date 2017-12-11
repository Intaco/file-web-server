import java.net._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp.Event
import akka.util.ByteString
import http.Method.{GET, HEAD}
import http._
import org.apache.tika.Tika
import utils.ResponseUtils._

case object WriteFinished extends Event

class SimplisticHandler(connection: ActorRef) extends Actor with ActorLogging {

  import akka.io.Tcp._

  def receive = {
    case Received(data) => {
      val requestData = toRequestData(data.utf8String)
      val builder = new ResponseHeadersBuilder()
      if (requestData.requestPath.split("/").exists(_.startsWith(".."))) {
        val response = builder.responseLine(requestData.protocol, NotFound).build
        sender() ! Write(ByteString(response), WriteFinished)
      } else if (requestData.method != GET && requestData.method != HEAD) {
        val response = builder.responseLine(requestData.protocol, MethodNotAllowed).build
        sender() ! Write(ByteString(response), WriteFinished)
      } else {
        var filePathString = URLDecoder.decode(ROOT + requestData.requestPath, StandardCharsets.UTF_8.name())
        val pathProbe = Paths.get(filePathString)

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
          if (requestData.method == GET) {
            import java.io.{FileInputStream, IOException}
            try {
              val ios = new FileInputStream(filePathString)
              try {
                val buffer = new Array[Byte](1024)
                var read = ios.read(buffer)
                while (read != -1) {
                  sender() ! Write(ByteString(buffer), NoAck)
                  read = ios.read(buffer)
                }
              } catch {
                case e: IOException =>
                  log.error(s"something went wrong... ${e.getMessage}")
              } finally if (ios != null) ios.close()
              sender() ! Close
            }
          }
          else
            sender() ! Close
        } else if (isExistingDir) {
          sender() ! Write(ByteString(emptyResponseBody(requestData.protocol, Forbidden)), WriteFinished)
        } else {
          val response = builder.responseLine(requestData.protocol, NotFound)
            .headerLine("Server", "AkkaHttpFileServer")
            .headerLine("Date", currentTime())
            .headerLine("Connection", "close")
            .emptyLine()
            .build
          sender() ! Write(ByteString(response), WriteFinished)
        }
      }
    }

    case WriteFinished =>
      sender() ! Close

    case PeerClosed => context stop self
  }

  private val chunkSize = 512 * 1024

  private def createWrites(filePathString: String, contentLength: Long, pos: Long = 0): WriteCommand = {
    val posDiff = contentLength - pos // should never be 0
    val requiresNextWrite = posDiff > chunkSize
    val thisChunk = if (requiresNextWrite) {
      chunkSize
    } else posDiff

    if (!requiresNextWrite) {
      WriteFile(filePathString, pos, thisChunk, WriteFinished)
    } else {
      WriteFile(filePathString, pos, thisChunk, NoAck) +: createWrites(filePathString, contentLength, pos + thisChunk)
    }
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