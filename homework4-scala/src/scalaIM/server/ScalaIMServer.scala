package scalaIM.server

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.{ServerSocket, Socket}
import java.util.concurrent._

import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.collection.mutable.ArrayBuffer

/** The ScalaIM Server.
  *
  * It's the entity delegated to accept connections and maintain the state of the chat (rooms, registered
  * users, etc...).
  */
object ScalaIMServer {

  /** The currently active rooms, together with a list of their users. */
  val rooms: concurrent.Map[String, ArrayBuffer[User]] = new ConcurrentHashMap[String, ArrayBuffer[User]]().asScala
  /** The list of registered users. */
  val users: concurrent.Map[String, User] = new ConcurrentHashMap[String, User]().asScala

  /** A representation of a client connection inside the server.
    *
    * It's the entity delegated to maintain the connection with a specific client
    * and serve their requests.
    *
    * @param socket The [[java.net.Socket Socket]] on which the client is connected.
    */
  class ClientRepr(socket: Socket) extends Runnable {
    private var in: BufferedReader = _
    private var out: PrintWriter = _
    private var thisUser: Option[User] = None
    private var exit = false

    /** Parses the received message into one of the classes recognized
      * by the server.
      *
      * @param received The received message.
      * @return A [[scalaIM.server.Message Message]] object.
      */
    def parseMessage(received: String): Message = {
      var toParse = received.trim
      toParse(0) match {
        case '#' =>
          val parsed = (toParse substring 1).split(" ", 2)
          if (parsed.length == 2)
            RoomMessage(parsed(1), parsed(0))
          else
            RoomMessage("", parsed(0))
        case '@' =>
          val parsed = (toParse substring 1).split(" ", 2)
          if (parsed.length == 2)
            Whisper(parsed(1), parsed(0))
          else
            Whisper("", parsed(0))
        case '/' =>
          val parsed = (toParse substring 1).split(" ")
          Request(parsed(0), parsed.drop(1).toList)
        case _ => Invalid(toParse)
      }
    }

    /** Processes and serves a request to the server.
      *
      * @param what The type of the request.
      * @param args Optional arguments for the request.
      * @param writer A [[java.io.PrintWriter PrintWriter]] object to the current client, to be inserted into
      *               a [[scalaIM.server.User User]] object in case of successful login/registration.
      * @return A [[String]] representing the server's response to the request.
      */
    def serveRequest(what: String, args: List[String], writer: PrintWriter): String = {
      what match {
        case "login" =>
          if (args.length < 2)
            "Missing parameter, usage: /login username password"
          else if (thisUser.isEmpty) {
            val name = args.head
            users get name match {
              case None =>
                "User " + name + " is not registered. Register it with /register user_name password"
              case Some(user) =>
                if (user.isOnline)
                  "User is already logged in"
                else
                if (user.password equals args(1)) {
                  user.login(out)
                  thisUser = Some(user)
                  "User " + name + ": login OK"
                } else
                  "Wrong password, try again"
            }
          } else "You are already logged in as user " + thisUser.get.name
        case "pass" =>
          if (args.length < 2)
            "Missing parameter, usage: /pass current_password new_password"
          else if (thisUser.isEmpty)
            "You are not logged in"
          else {
            if (thisUser.get.changePwd(args.head, args(1)))
              "Password changed"
            else
              "Wrong current password"
          }
        case "logout" =>
          if (thisUser.isEmpty)
            "Currently not logged in, use /exit to disconnect"
          else {
            thisUser.get.logout()
            thisUser = None
            "Logged out, use /exit to disconnect"
          }
        case "exit" =>
          if (thisUser.isDefined) {
            thisUser.get.logout()
          }
          exit = true
          "Disconnecting. Bye!"
        case "join" =>
          if (args.isEmpty)
            "Missing parameter, usage: /join room_name"
          else {
            if (thisUser.isEmpty)
              "Currently not logged in"
            else {
              val name = args.head
              rooms.get(name) match {
                case Some(room) =>
                  if (room.contains(thisUser.get)) {
                    "You have already joined room " + name
                  }
                  else {
                    room foreach { u =>
                      if (u.isOnline) {
                        u.currWriter.get.println("[@" + thisUser.get.name + " entered room #" + name + "]")
                      }
                    }
                    room.insert (0, thisUser.get)
                    "Joined room " + name
                  }
                case None =>
                  rooms.put(name, ArrayBuffer(thisUser.get))
                  "Room " + name + " didn't exist. It has been created and joined"
              }
            }
          }
        case "leave" =>
          if (args.isEmpty)
            "Missing parameter, usage: /leave room_name"
          else {
            if (thisUser.isEmpty)
              "Currently not logged in"
            else {
              val name = args.head
              rooms.get(name) match {
                case Some(room) =>
                  if (room.contains(thisUser.get)) {
                    room -= thisUser.get
                    if (room.isEmpty)
                      rooms.remove(name)
                    else
                      room foreach { u =>
                        if (u.isOnline) {
                          u.currWriter.get.println("[@" + thisUser.get.name + " left room #" + name + "]")
                        }
                      }
                    "You left room " + name
                  } else {
                    "You've not entered room " + name
                  }
                case None =>
                  "Room " + name + " doesn't exist"
              }
            }
          }
        case "register" =>
          if (args.length < 2)
            "Missing parameters. Usage: /register user_name password"
          else if (thisUser.isDefined)
            "Already registered and logged in"
          else {
            val name = args.head
            val pw = args(1)
            users.get(name) match {
              case Some(_) =>
                "Username " + name + " already exists"
              case None =>
                val u = new User(name, pw)
                u.login(writer)
                users.put(name, u)
                thisUser = Some(u)
                "User " + name + " registered and logged in!"
            }
          }
        case "users" =>
          if (args.isEmpty)
            "Missing parameter. Usage: /users room"
          else {
            if (thisUser.isEmpty)
              "Currently not logged in"
            else {
              val name = args.head
              rooms.get(name) match {
                case Some(room) =>
                  val stb = new StringBuilder("Online users in room " + name + ": [")
                  var primo = true
                  room foreach { u =>
                    if (u.isOnline) {
                      if (!primo)
                        stb.append(", ")
                      stb.append(u.name)
                      primo = false
                    }
                  }
                  stb.append("]")
                  stb.toString()
                case None =>
                  "Room " + name + " doesn't exist"
              }
            }
          }
        case "help" =>
          "List of available commands:\n" +
            "/help\t\t\tDisplays this information\n" +
            "/register nick pass\tRegisters and logs in\n" +
            "/login nick pass\tLogs into the service\n" +
            "/pass old new\t\tChanges the current user's password\n" +
            "/logout\t\t\tLogs out of the service without disconnecting from it\n" +
            "/exit\t\t\tDisconnect from the service\n" +
            "/join room\t\tJoins the selected room\n" +
            "/leave room\t\tLeaves the selected room\n" +
            "/users room\t\tGet the list of users currently in the room"
        case s =>
          "Unrecognized command /" + s + ", try again"
      }
    }

    /** An utility method to check if the current room contains a specific user.
      *
      * @param room The list of users of the room.
      * @param user The user to be checked.
      * @return True if the user is in the room.
      */
    def roomContainsUser(room: ArrayBuffer[User], user: String): Boolean = {
      !(room forall { u => !(u.name equals user)})
    }

    /** Sends a message to all the users of a specific room.
      *
      * @param username The name of the user that sends the message.
      * @param content The message.
      * @param dest The name of the room.
      * @return An [[Option]] that can be <ul>
      *           <li>None, if the message has been sent successfully</li>
      *           <li>Some([[String]]), if there's an error. The string represents the server's response</li></ul>
      */
    def sendRoomMessage(username: String, content: String, dest: String): Option[String] = {
      if (content.isEmpty) Some("You sent an empty message")
      else {
        val room = rooms.get(dest)
        if (room.isDefined)
          if (roomContainsUser(room.get, username)) {
            room.get foreach { user =>
              if (!user.equals(username)) {
                if (user.isOnline) {
                  val pw = user.currWriter.get
                  pw.println("[#" + dest + ": " + username + "] " + content)
                }
              }
            }
            None
          }
          else Some("You have not joined room " + dest + ". Join it with /join " + dest)
        else Some("Room #" + dest + " does not exist")
      }
    }

    /** Sends a private message to another user.
      *
      * @param username The name of the user that sends the message.
      * @param content The message.
      * @param dest The name of the recipient user.
      * @return An [[Option]] that can be <ul>
      *           <li>None, if the message has been sent successfully</li>
      *           <li>Some([[String]]), if there's an error. The string represents the server's response</li></ul>
      */
    def sendWhisper(username: String, content: String, dest: String): Option[String] = {
      if (content.isEmpty) Some("You sent an empty message")
      else {
        val recipient = users.get(dest)
        if (recipient.isDefined)
          if (recipient.get.isOnline) {
            val pw = recipient.get.currWriter.get
            pw.println("[@" + username + "] " + content)
            None
          } else Some("User is not online at the moment")
        else Some("No user with that name")
      }
    }


    /** The main method of the [[scalaIM.server.ScalaIMServer.ClientRepr ClientRepr]] thread. */
    override def run(): Unit = {

      try {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream))
        out = new PrintWriter(socket.getOutputStream, true)
        out.println("Welcome to ScalaIM! Type /help if you don't know what to do :)")
        while (!exit) {
          val line = in.readLine()
          parseMessage(line) match {
            case Invalid(s) =>
              out.println("[SERVER] Cannot understand command \"" + s +
                "\"\nTry /help for a list of available commands")
            case Request(what, args) =>
              out.println("[SERVER] " + serveRequest(what, args, out))
            case RoomMessage(what, where) if thisUser.isDefined =>
              val outcome = sendRoomMessage(thisUser.get.name, what, where)
              if (outcome.isDefined)
                out.println("[SERVER] " + outcome.get)
            case Whisper(what, who) if thisUser.isDefined =>
              val outcome = sendWhisper(thisUser.get.name, what, who)
              if (outcome.isDefined)
                out.println("[SERVER] " + outcome.get)
            case _ =>
              out.println("[SERVER] Not logged in. Login with /login username password")
          }
        }
      } finally {
        if (thisUser.isDefined) thisUser.get.logout()
        in.close()
        out.close()
      }
    }
  }

  /** The main function of the server.
    *
    * @param args Unused.
    */
  def main(args: Array[String]): Unit = {

    val executor: ExecutorService = Executors.newFixedThreadPool(Parameters.conc_users + 1)
    var serverSocket: ServerSocket = null
    var end = false

    val port = Parameters.port
    if (port > -1) {
      try {
        serverSocket = new ServerSocket(Parameters.port)
        val address = serverSocket.getInetAddress

        executor.execute({ () =>
          while (!end) {
            val newconn = serverSocket.accept()

            try {
              executor.execute(new ClientRepr(newconn))
            } catch {
              case e: RejectedExecutionException =>
                println("Reached maximum number of concurrent users (ignore if shutting down)")
                newconn.close()
            }
          }
        })

        println("Server started! Type \"quit\" to exit")
        while (!end) {
          val reading = io.StdIn.readLine()
          if (reading equals "quit") end = true
        }

        println("Shutting down after every user logs out")
        new Socket(address, Parameters.port).close()
        executor.shutdown()
        executor.awaitTermination(Long.MaxValue, TimeUnit.NANOSECONDS)

      } finally {
        serverSocket.close()
      }
    }


  }

}
