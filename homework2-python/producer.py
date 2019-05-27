from common import *
import threading
import time

class Producer(threading.Thread):
    """
        An entity that generates packets and puts them into one of the switch's input interfaces.
        It abstracts the process of packet composition and forwarding by a source terminal node.
    """
    def __init__(self, id, switch, srcrange, dstrange, ports, delay=0.5, sleeptime=0.333, no_packets=float('inf'), step=False):
        """
            The constructor for an object of type Producer.

            id: An unique integer that identifies this object.
            switch: A pointer to an object of class Switch.
            srcrange: A range of IP Addresses to be used in the src field of generated packets.
            dstrange: A range of IP Addresses to be used in the dst field of generated packets.
            ports: A range of (transport-level) ports to be used in the payload of generated packets.
            delay: Time waited before beginning the production of packets.
            sleeptime: Time waited between the generation of any two packets.
            no_packets: Number of packets to be generated (possibly infinite).
            step: Whether the simulation is being run in step-by-step or not
        """
        super().__init__()
        self.id = id
        self.putpacket = switch.put_packet
        self.sleep_time = sleeptime
        self.no_packets = no_packets
        self.sources = srcrange
        self.dests = dstrange
        self.destports = ports
        self.count1 = 0 #
        self.count2 = 0 # Counters for the packet generation
        self.count3 = 0 #
        self.delay = delay
        self.step = step
        self.stepWait = step
        self.__stopped = False

    def stop(self):
        """
            Stops the execution of this producer.
        """
        self.no_packets = -float('inf')
        self.stepWait = False
        self.__stopped = True

    def do_step(self, isEnd=False):
        """
            Advances the execution in step-by-step mode.

            isEnd: True if after this step the execution should end.
        """
        self.stepWait = False
        if isEnd:
            self.stop()

    def generator(self):
        """
            Packet generator.

            yields: A packet.
        """
        def operation(num):
            if num % 4 == 0:
                return 'r'
            elif num % 4 == 1:
                return 'w'
            elif num % 4 == 2:
                return 'rw'
            else:
                return 'o'

        while True:
            payload = {Strings.DEST_PORT:self.destports[self.count3], Strings.OP:operation(self.count1)}
            yield {Strings.SRC_ADDR:self.sources[self.count1],
                   Strings.DST_ADDR:self.dests[self.count2],
                   Strings.PAYLOAD:payload}
            self.count1 += 1
            if self.count1 >= len(self.sources): self.count1 = 0
            self.count2 += 1
            if self.count2 >= len(self.dests): self.count2 = 0
            self.count3 += 1
            if self.count3 >= len(self.destports): self.count3 = 0


    def run(self):
        """
            Main lifecycle of the producer.
        """
        def prettyprint(message):
            """
                Utility method to print a message with a yellow-colored "Producer #number: " prefix.

                message: The message to be printed.
            """
            print(Color.YELLOW + Color.BOLD + "Producer #" + str(self.id) + ": " + Color.ENDC + message)
        if not self.step:
            time.sleep(self.delay)
            print("Producer #" + str(self.id) + " delayed " + str(self.delay))
        i = 0
        gen = self.generator()
        while i < self.no_packets and not self.__stopped:
            while self.stepWait:
                pass
            self.stepWait = self.step
            i += 1
            if not self.__stopped:
                pkt = next(gen)
                prettyprint("sending packet " + packet_to_string(pkt))
                self.putpacket(self.id, pkt)
            else:
                self.putpacket(-1, None)
            if not self.step:
                time.sleep(self.sleep_time)