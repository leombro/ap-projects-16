from threading import Thread
from common import Strings, Color, packet_to_string

class Consumer(Thread):
    """
        An entity that "consumes" packets. It abstracts the process of receiving and decapsulating/demuxing packets
        by a destination terminal node.
    """
    def __init__(self, outlink, id):
        """
            The constructor for an object of type Consumer .

            outlink: The switch's output interface connected to this consumer.
            id: An unique integer that identifies this object.
        """
        super().__init__()
        self.queue = outlink
        self.__stop = False
        self.id = id
        self.__pkts = [] # List of received packets.

    def print_rec_pkts(self):
        """
            An utility function to print the packets received insofar.
        """
        toprint = "\t\t" + Color.PINK + Color.BOLD + "CONSUMER #" + str(self.id) + ": CONSUMED PACKETS" + Color.ENDC + '\n'
        toprint += "---------------------------------------------------------------\n"
        toprint += " " + Color.BOLD + "FROM\t\tTO\t\tPAYLOAD" + Color.ENDC + '\n'
        toprint += "---------------------------------------------------------------\n"
        for pkt in self.__pkts:
                toprint += " {0}\t{1}\t{2}\n".format(pkt[Strings.SRC_ADDR],
                                                      pkt[Strings.DST_ADDR],
                                                      pkt[Strings.PAYLOAD])
        toprint += "---------------------------------------------------------------\n"
        toprint += Color.ENDC
        print(toprint)

    def run(self):
        """
            Main lifecycle of the consumer.
        """
        def prettyprint(message):
            """
                Utility method to print a message with a pink-colored "Consumer #number: " prefix.

                message: The message to be printed.
            """
            print(Color.PINK + Color.BOLD + "Consumer #" + str(self.id) + ": " + Color.ENDC + message)
        while not self.__stop:
            resp = self.queue.get()

            if not resp:
                raise ValueError

            if resp == 'STOP':
                self.__stop = True
            else:
                self.__pkts.append(resp)
                prettyprint("consumed packet " + packet_to_string(resp))

        prettyprint(Color.BOLD + Color.RED + "ending" + Color.ENDC)