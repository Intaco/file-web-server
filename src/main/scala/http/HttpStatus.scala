package http

abstract class HttpStatus(val code: Int)

case object OK extends HttpStatus(code = 200) {
  override def toString: String = code + " OK"
}
case object NotFound extends HttpStatus(code = 404) {
  override def toString: String = code + " Not Found"
}
case object MethodNotAllowed extends HttpStatus(code = 405) {
  override def toString: String = code + " Method Not Allowed"
}