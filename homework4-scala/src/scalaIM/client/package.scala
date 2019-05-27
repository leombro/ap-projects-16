package scalaIM

/** A simple client with TELNET-like capabilities.
  * It reads information from a config file ("client.cfg") and then
  * <ul><li>prints everything it reads from the server</li>
  * <li>reads lines from standard input and sends them to the server.</li></ul>
  */
package object client {

  /** A simple exception thrown when the config file
    * is not well formed or misses important information.
    * @param message The message to show when the exception is raised.
    */
  class ConfigurationException(message: String) extends RuntimeException(message: String)
}
