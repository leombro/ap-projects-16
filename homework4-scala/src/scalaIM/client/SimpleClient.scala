package scalaIM.client

import java.net.{InetAddress, Socket, SocketException, UnknownHostException}
import java.io._
import java.util.concurrent.{Executors, TimeUnit}

import scala.io.Source
import scala.util.Try

/** The core of the client program. */
object SimpleClient {
  /** The main function of the client.
    *
    * First of all, it reads from the configuration file ("client.cfg") the socket parameters;
    * then, it attempts to connect to the specified socket, and if successful it creates two
    * threads. The first thread continuously prints on stdout every line received from the server,
    * while the second thread reads lines from stdin and sends them to the server.
    *
    * @param args Unused.
    */
  def main(args: Array[String]): Unit = {
    val filename = "client.cfg"
    var port: Int = -1
    var address: InetAddress = null

    try {
      for (line <- Source.fromFile(filename).getLines()) {
        val rule = line.split(":")
        if (rule.length == 2) {
          rule(0) match {
            case "address" => address = InetAddress.getByName(rule(1))
            case "port" => port = rule(1).toInt
          }
        }
      }
      if (address == null || port == -1)
        throw new ConfigurationException("Could not initialize, config file missing necessary rules")
    } catch {
      case e: NumberFormatException => throw new IOException(e.getMessage)
      case _: IOException => throw new IOException("Error reading config file")
      case _: FileNotFoundException => throw new IOException("File \"" + filename + "\" not found")
      case _: UnknownHostException => throw new IOException("Address format is not valid, check config file")
    }

    val mSock = Try[Socket](new Socket(address, port))
    if (mSock.isFailure)
      println("Could not connect to the server")
    else {
      val socket = mSock.get
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
      val out = new OutputStreamWriter(socket.getOutputStream)
      val consoleIn = new BufferedReader(new InputStreamReader(System.in))
      val ex = Executors.newFixedThreadPool(2)
      var end = false

      val threadWrite = new Thread({ () =>
        try {
          while (!end) {
            if (consoleIn.ready()) {
              val rd = consoleIn.readLine()
              if (rd != null && !(rd equals "")) {
                out.write(rd + "\n")
                out.flush()
              }
            }
          }
        } catch {
          case _: SocketException => println("Server disconnected! Closing...")
          case _: IOException => println("Closing...")
        }
      })

      val threadRead = new Thread({ () =>
        try {
          var rd = ""
          while (rd != null) {
            println(rd)
            rd = in.readLine()
          }
          in.close()
          end = true
          consoleIn.close()
        } catch {
          case _: SocketException => println("Closing...")
        }
      })
      try {
        ex execute threadRead
        ex execute threadWrite
        ex.shutdown()
        ex.awaitTermination(Long.MaxValue, TimeUnit.NANOSECONDS)
      } finally {
        ex.shutdownNow()
        in.close()
        out.close()
        socket.close()
      }
    }
  }
}
