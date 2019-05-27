package scalaIM

import scala.io.Source

/** The ScalaIM server.
  *
  * It provides an IRC-like chat service with support for an unlimited number
  * of different rooms and the possibility for users to send each other private
  * messages.
  */
package object server {

  /** A simple exception thrown when the config file
    * is not well formed or misses important information.
    * @param message The message to show when the exception is raised.
    */
  class ConfigurationException(message: String) extends RuntimeException(message: String)

  /** An abstract representation of a message received from a client.
    *
    * @param content The content of the message.
    */
  abstract class Message (content: String)

  /** A message which is interpreted as a request to the server.
    *
    * @param content The request.
    * @param args The arguments for the request, possibly none.
    */
  case class Request(content: String, args: List[String]) extends Message(content)

  /** A message to be delivered to all the users of a room.
    *
    * @param content The content of the message.
    * @param destination The name of the room.
    */
  case class RoomMessage(content: String, destination: String) extends Message(content)

  /** A message directed to a specific user.
    *
    * @param content The content of the message.
    * @param to The name of the receiver.
    */
  case class Whisper(content: String, to: String) extends Message(content)

  /** A message that could not be understood by the server.
    *
    * @param content The content of the message.
    */
  case class Invalid(content: String) extends Message(content)

  /** An object that parses the config file and stores the rules. */
  object Parameters {

    /* True if the config has already been parsed. */
    private var setup = false

    /** The port number for the server. */
    private var mPort: Int = -1

    /** The number of allowable concurrent users. */
    private var mUsers: Int = -1

    /** Returns the port number, parsing the configuration file if the field
      * has not been initialized.
      *
      * @return The port number.
      */
    def port: Int = {
      if (!setup) {
        start()
        port
      }
      else mPort
    }

    /** Returns the maximum number of concurrent users, parsing the configuration file if the field
      * has not been initialized.
      *
      * @return The maximum number of concurrent users.
      */
    def conc_users: Int = {
      if (!setup) {
        start()
        conc_users
      }
      else mUsers
    }

    /** Parses the configuration file.*/
    private def start() = {
      val filename = "server.cfg"
      try {
        for (line <- Source.fromFile(filename).getLines()) {
          val rule = line.split(":")
          if (rule.length == 2) {
            rule(0) match {
              case "port" => mPort = rule(1).toInt
              case "concurrent_users" => mUsers = rule(1).toInt
            }
          }
        }
        if (mPort != -1 && mUsers != -1) setup = true
        else throw new ConfigurationException("Could not initialize, config file missing necessary rules")
      } catch {
        case e1: java.io.IOException => throw new java.io.IOException("Error reading config file")
        case e2: NumberFormatException => throw new java.io.IOException(e2.getMessage)
        case e2: java.io.FileNotFoundException => throw new java.io.IOException("File \"" + filename + "\" not found")
      }
    }
  }
}
