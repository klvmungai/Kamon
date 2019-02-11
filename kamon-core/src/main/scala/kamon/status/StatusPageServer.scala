package kamon.status

import java.io.InputStream

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.{Status => StatusCode}
import java.util.Collections
import java.util.concurrent.{ExecutorService, Executors}

import scala.collection.JavaConverters.asScalaBufferConverter

class StatusPageServer(hostname: String, port: Int, resourceLoader: ClassLoader, status: Status)
    extends NanoHTTPD(hostname, port) {

  private val RootResourceDirectory = "status"
  private val ResourceExtensionRegex = ".*\\.([a-zA-Z0-9]*)".r

  override def serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = {
    if(session.getMethod() == NanoHTTPD.Method.GET) {
      if(session.getUri().startsWith("/status")) {

        // Serve the current status data on Json.
        session.getUri() match {
          case "/status/settings"         => json(status.settings())
          case "/status/modules"          => json(status.moduleRegistry())
          case "/status/metrics"          => json(status.metricRegistry())
          case "/status/instrumentation"  => json(status.instrumentation())
          case _                          => NotFound
        }

      } else {

        // Serve resources from the status page folder.
        val requestedResource = if (session.getUri() == "/") "/index.html" else session.getUri()
        val resourcePath = RootResourceDirectory + requestedResource
        val resourceStream = resourceLoader.getResourceAsStream(resourcePath)

        if (resourceStream == null) NotFound else resource(requestedResource, resourceStream)
      }

    } else NotAllowed
  }

  override def start(): Unit = {
    setAsyncRunner(new ThreadPoolRunner(Executors.newFixedThreadPool(2)))
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
  }

  private def mimeType(resource: String): String = {
    val ResourceExtensionRegex(resourceExtension) = resource
    resourceExtension match {
      case "css"    => "text/css"
      case "js"     => "application/javascript"
      case "ico"    => "image/x-icon"
      case "svg"    => "image/svg+xml"
      case "html"   => "text/html"
      case "woff2"  => "font/woff2"
      case _        => "text/plain"
    }
  }

  private def json[T](instance: T)(implicit marshalling: JsonMarshalling[T]) = {
    val builder = new java.lang.StringBuilder()
    marshalling.toJson(instance, builder)

    val response = NanoHTTPD.newFixedLengthResponse(StatusCode.OK, "application/json", builder.toString())
    response.closeConnection(true)
    response
  }

  private def resource(name: String, stream: InputStream) = {
    val response = NanoHTTPD.newChunkedResponse(StatusCode.OK, mimeType(name), stream)
    response.closeConnection(true)
    response
  }

  private val NotAllowed = NanoHTTPD.newFixedLengthResponse(
    StatusCode.METHOD_NOT_ALLOWED,
    NanoHTTPD.MIME_PLAINTEXT,
    "Only GET requests are allowed."
  )

  private val NotFound = NanoHTTPD.newFixedLengthResponse(
    StatusCode.NOT_FOUND,
    NanoHTTPD.MIME_PLAINTEXT,
    "The requested resource was not found."
  )

  // Closing the connections will ensure that the thread pool will not be exhausted by keep alive
  // connections from the browsers.
  NotAllowed.closeConnection(true)
  NotFound.closeConnection(true)


  /**
    * AsyncRunner that uses a thread pool for handling requests rather than spawning a new thread for each request (as
    * the default runner does).
    */
  private class ThreadPoolRunner(executorService: ExecutorService) extends NanoHTTPD.AsyncRunner {
    final private val _openRequests = Collections.synchronizedList(new java.util.LinkedList[NanoHTTPD#ClientHandler]())

    override def closeAll(): Unit =
      _openRequests.asScala.foreach(_.close())

    override def closed(clientHandler: NanoHTTPD#ClientHandler): Unit =
      _openRequests.remove(clientHandler)

    override def exec(clientHandler: NanoHTTPD#ClientHandler): Unit = {
      executorService.submit(clientHandler)
      _openRequests.add(clientHandler)
    }
  }
}