package utils

import java.text.SimpleDateFormat
import java.util.Date

import http._

object ResponseUtils {

  val ROOT: String = "./"

  def toRequestData(request: String): RequestData = {
    val lines = request.split("\n")
    if (lines.isEmpty) RequestData.empty
    else {
      val requestLineArgs = lines(0).replaceAll("\r", "").split(" ")
      val method = Method(requestLineArgs(0))
      val path = requestLineArgs(1).split("\\?")(0)
      val protocol = requestLineArgs(2)
      RequestData(method, path, protocol)
    }
  }

  private val responseDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z")

  def currentTime(): String = responseDateFormat.format(new Date())

  class ResponseHeadersBuilder {
    private var builder: StringBuilder = new StringBuilder

    private def line(args: Any*): ResponseHeadersBuilder = {
      args.foreach(builder ++= _.toString)
      builder ++= "\r\n"
      this
    }

    def responseLine(protocol: String, status: HttpStatus): ResponseHeadersBuilder = line(protocol, " ", status)

    def headerLine(name: String, value: String): ResponseHeadersBuilder = line(name, ": ", value)

    def emptyLine(): ResponseHeadersBuilder = {
      builder ++= "\r\n"
      this
    }

    def build: String = builder.toString()

  }

}
