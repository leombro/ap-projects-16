package scalaIM.server

import java.io.PrintWriter

/** Represents a chat user in the server.
  *
  * @param name The user's name.
  * @param password The user's password.
  */
class User(var name: String, var password: String) {

  /** The output channel towards the user, if he/she is logged in.*/
  var currWriter: Option[PrintWriter] = None

  /** Changes the user's password.
    *
    * @param curr The old password.
    * @param to The new password.
    * @return True if the password has been changed, false otherwise.
    */
  def changePwd(curr: String, to: String): Boolean = {
    if (curr equals password) {
      password = to
      true
    } else false
  }

  /** Check if the user is logged in.
    *
    * @return True if the user is logged in, false otherwise.
    */
  def isOnline: Boolean = currWriter match {
    case None => false
    case _ => true
  }

  /** Acknowledges that the user has successfully logged in, and stores the communication channel inside
    * this object.
    *
    * @param printWriter The channel for communicating with the user.
    * @return False if the user was already logged in, true otherwise.
    */
  def login(printWriter: PrintWriter): Boolean = currWriter match {
    case None => currWriter = Some(printWriter); true
    case _ => false
  }

  /** Acknowledges that the user has successfully logged out, discarding the communication channel.
    *
    * @return False if the user was not logged in, true otherwise.
    */
  def logout(): Boolean = currWriter match {
    case Some(_) => currWriter = None; true
    case None => false
  }

  override def equals(obj: scala.Any): Boolean =
    obj match {
      case u: User => u.name equals this.name
      case s: String => s equals this.name
      case _ => false
    }
}