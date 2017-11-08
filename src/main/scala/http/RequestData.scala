package http

import http.Method.{HttpMethod, UNDEFINED}

case class RequestData(method: HttpMethod, requestPath: String, protocol: String){
  def isEmpty: Boolean = this == RequestData.empty
}


object RequestData {
  val empty = RequestData(UNDEFINED, "", "HTTP/1.1")
}