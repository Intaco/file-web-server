package http

object Method {

  abstract class HttpMethod(val name: String) {
    override def toString: String = name
  }

  case object GET extends HttpMethod("GET")

  case object HEAD extends HttpMethod("HEAD")

  case object UNDEFINED extends HttpMethod("Undefined")

  def apply(methodString: String): HttpMethod = methodString match {
      case "GET" => GET
      case "HEAD" => HEAD
      case _ => UNDEFINED
    }
}
