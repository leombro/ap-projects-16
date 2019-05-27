import queue
from common import *
from sys import stdout

class Switch():
    """
        A representation of a network switch.
    """
    def __init__(self, controller, no_outports):
        """
            The constructor for objects of type Switch.

            controller: A pointer to a controller object, representing the network's controller.
            no_outports: The number of output interfaces of the switch.
        """
        self.controller_link = controller
        self.inqueue = queue.Queue() # Logical queue for incoming packets.
        self.outports = [queue.Queue() for _ in range(no_outports)] # List of queues representing output interfaces.
        self.ftab = {} # The routing table.
        self.policies = None # List of currently enforced policies.
        self.filters = None # List of currently active filters.
        self.redirects = None # List of currently active redirects (mobility).
        self.responses = [] # List of messages received from the controller.
        self.dropped = [] # List of dropped packets.
        self.end = False # Whether the switch has received a termination signal.

    def put_packet(self, inport, pkt):
        """
            Puts a packet into this switch's input queue.

            inport: The identifier of the input interface.
            pkt: The packet to be inserted.
        """
        self.inqueue.put((inport, pkt))
        
    def prettyprint(self, message):
        """
            Utility method to print a message with a green-colored "Switch: " prefix.

            message: The message to be printed.
        """
        print(Color.GREEN + Color.BOLD + "Switch: " + Color.ENDC + message)

    def print_ftab(self):
        """
            Utility method to print the current state of the routing table.
        """
        self.prettyprint("Printing current forwarding table")
        toprint = Color.BOLD
        toprint += "---------------------------------------------------------------\n"
        toprint += " Destination\t\tPort\t\tActive\n"
        toprint += "---------------------------------------------------------------\n"
        for rule in sorted(list(self.ftab)):
            toprint += " {0}\t\t{1}\t\t{2}\n".format(rule,
                                                      (self.ftab[rule])[Strings.OUTLINK],
                                                      (self.ftab[rule])[Strings.ACTIVE])
        toprint += "---------------------------------------------------------------\n"
        toprint += Color.ENDC
        stdout.write(toprint)

    def print_policies(self):
        """
            Utility method to print the current active policies and their state.
        """
        self.prettyprint("Printing current policies")
        toprint = Color.BOLD
        toprint += "-------------------------------------------------------------------------\n"
        toprint += " (From, To)\t\t\tREADs\t\tWRITEs\n"
        toprint += "-------------------------------------------------------------------------\n"
        if (self.policies):
            for pair, rem in list(zip(self.policies.keys(), self.policies.values())):
                if (pair == Strings.POLICIES):
                    pairname = 'DEFAULT\t'
                else:
                    pairname = "({0}, {1})".format(str(pair[0]).replace("'", ""), str(pair[1]).replace("'", ""))
                toprint += " {0}\t\t{1}\t\t{2}\n".format(pairname,
                                                         (self.policies[pair])[0],
                                                         (self.policies[pair])[1])
        else:
            toprint += " No policy currently active\n"
        toprint += "-------------------------------------------------------------------------\n"
        toprint += Color.ENDC
        stdout.write(toprint)

    def print_mobility(self):
        """
            Utility method to print the current active redirects.
        """
        self.prettyprint("Printing current redirects")
        toprint = Color.BOLD
        toprint += "-------------------------------------------------------------------------\n"
        toprint += " Old destination\tNew destination\n"
        toprint += "-------------------------------------------------------------------------\n"
        if (self.redirects):
            for src, dst in list(zip(self.redirects.keys(), self.redirects.values())):
                toprint += " {0}\t\t{1}\n".format(str(src).replace("'", ""), str(dst).replace("'", ""))
        else:
            toprint += " No redirect currently active\n"
        toprint += "-------------------------------------------------------------------------\n"
        toprint += Color.ENDC
        stdout.write(toprint)

    def print_filters(self):
        """
            Utility method to print the current active filters.
        """
        self.prettyprint("Printing current active filters")
        toprint = Color.BOLD
        toprint += "-------------------------------------------------------------------------\n"
        toprint += " Filter no.\tDescription\n"
        toprint += "-------------------------------------------------------------------------\n"
        if (self.filters):
            i = 0
            for fil in list(self.filters):
                i += 1
                toprint += " {0}\t\t{1}\n".format(i,
                                                          fil.__doc__)
        else:
            toprint += " No filter currently active\n"
        toprint += "-------------------------------------------------------------------------\n"
        toprint += Color.ENDC
        stdout.write(toprint)

    def print_disc_pkts(self):
        """
            Utility method to print the packets discarded insofar and the motivation for their rejection.
        """
        toprint = "\t\t" + Color.GREEN + Color.BOLD + "SWITCH: DROPPED PACKETS" + Color.ENDC + '\n'
        toprint += "---------------------------------------------------------------\n"
        toprint += " " + Color.BOLD + "FROM\t\tTO\t\tREASON" + Color.ENDC + '\n'
        toprint += "---------------------------------------------------------------\n"
        for (pkt, mot) in self.dropped:
            toprint += " {0}\t{1}\t{2}\n".format(pkt[Strings.SRC_ADDR],
                                                  pkt[Strings.DST_ADDR],
                                                  mot)
        toprint += "---------------------------------------------------------------\n"
        toprint += Color.ENDC
        stdout.write(toprint)

    def start(self):
        """
            The switch's main loop.
        """
        def enforce_policies(packet):
            """
                Checks that a packet passes the check on the currently active policy.

                The global policy is contained into the dictionary's POLICIES key; it is used as a "blueprint".
                Every time a packet is processed, its (source, destination) pair is checked. If this is the first
                packet with such a pair, the global policy is copied into the dictionary using (source, destination)
                as a key, then the policy is enforced and the dictionary is accordingly updated.
                If the pair is already a valid key (i.e. a packet with the same pair was already processed) then the
                previous state is read from the dictionary and the policy enforced.

                packet: The packet against which the policy is enforced.
                return: A pair (string, bool) where the string is the motivation for the packet's rejection (None
                        if the packet is not rejected) and the boolean is whether the packet is allowed or not.
            """
            try:
                rem_r, rem_w = self.policies[(packet[Strings.SRC_ADDR], packet[Strings.DST_ADDR])]
            except KeyError:
                rem_r, rem_w = self.policies[Strings.POLICIES]
            op = (packet[Strings.PAYLOAD])[Strings.OP]
            if op == 'r':
                if rem_r > 0:
                    rem_r -= 1
                    self.policies[(packet[Strings.SRC_ADDR], packet[Strings.DST_ADDR])] = (rem_r, rem_w)
                    return None, True
                else:
                    return "max no. of 'READ's exceeded", False
            elif op == 'w':
                if rem_w > 0:
                    rem_w -= 1
                    self.policies[(packet[Strings.SRC_ADDR], packet[Strings.DST_ADDR])] = (rem_r, rem_w)
                    return None, True
                else:
                    return "max no. of 'WRITE's exceeded", False
            elif op == 'rw':
                if (rem_r > 0) and (rem_w > 0):
                    rem_r -= 1
                    rem_w -= 1
                    self.policies[(packet[Strings.SRC_ADDR], packet[Strings.DST_ADDR])] = (rem_r, rem_w)
                    return None, True
                elif rem_r > 0:
                    return "max no. of 'WRITE's exceeded", False
                elif rem_w > 0:
                    return "max no. of 'READ's exceeded", False
                else:
                    return "max no. of 'READ's and 'WRITE's exceeded", False
            else:
                return None, True
        def update():
            """
                Processes the messages sent by the controller.
            """
            if self.responses:
                resp = self.responses.pop(0)
                if resp[Strings.RESP_TYPE] == Strings.NEW_TABLE:
                    self.prettyprint("got a new table from the controller")
                    self.ftab = resp[Strings.RESP_DATA]
                    self.print_ftab()
                elif resp[Strings.RESP_TYPE] == Strings.UPDATED_TABLE:
                    self.prettyprint("controller updated the forwarding table")
                    self.ftab = resp[Strings.RESP_DATA]
                    self.print_ftab()
                elif resp[Strings.RESP_TYPE] == Strings.POLICIES:
                    self.prettyprint("controller installed security policies")
                    self.policies = resp[Strings.RESP_DATA]
                elif resp[Strings.RESP_TYPE] == Strings.FILTERS:
                    self.prettyprint("controller imposed filters")
                    self.filters = resp[Strings.RESP_DATA]
                elif resp[Strings.RESP_TYPE] == Strings.MOBILITY:
                    self.prettyprint("controller sent rules for handling mobility")
                    self.redirects = resp[Strings.RESP_DATA]
                elif resp[Strings.RESP_TYPE] == Strings.END:
                    self.prettyprint(Color.BOLD + Color.RED + "ending at next iteration" + Color.ENDC)
                    self.end = True
                update()

        # Beginning of the switch's operational life: request a routing table to the controller.
        self.prettyprint(Color.RED + "started" + Color.ENDC)
        self.controller_link.request(Strings.NEW_TABLE, self.responses, len(self.outports))
        self.prettyprint("initializing forwarding table")
        i = -1

        while not self.end: # Main cycle
            i += 1

            if i > 0 and (i % 3 == 0): # Request an update every three cycles
                self.prettyprint("requesting an update to the controller")
                self.controller_link.request(Strings.UPDATE, self.responses, None)
            else:
                update()

                (port, pkt) = self.inqueue.get() # Pick the first packet in the queue
                if (port == -1 and not pkt): # If the input interface is -1 and the packet is empty
                    self.end = True # This is interpreted as a termination signal
                else:
                    self.prettyprint("processing packet " + packet_to_string(pkt) + " from input port " + str(port))

                    filtered = False
                    if self.filters: # Apply the filters, if there is at least one
                        for filter in self.filters:
                            if filter(pkt):
                                self.prettyprint("packet "
                                            + packet_to_string(pkt)
                                            + " has been " + Color.RED + "blocked by a filter" + Color.ENDC)
                                self.dropped.append((pkt, "filtered"))
                                filtered = True
                                break # A packet is discarded if it satisfies at least a filter

                    if not filtered:
                        destination = pkt[Strings.DST_ADDR]
                        if self.redirects and destination in self.redirects.keys():
                            # Check if the packet's destination has moved, and reroute the packet accordingly
                            destination = self.redirects[destination]
                            self.prettyprint("packet "
                                        + packet_to_string(pkt)
                                        + " has been " + Color.YELLOW + "rerouted for mobility" + Color.ENDC)
                        try: # The routing table may not contain a rule for this destination
                            rule = (self.ftab)[destination]
                            if rule[Strings.ACTIVE]: # Link is active
                                motivation, cont = "", True
                                if self.policies:
                                    (motivation, cont) = enforce_policies(pkt)
                                if cont:
                                    # If there's no policy, or the packet doesn't violate the one currently active:
                                    out = rule[Strings.OUTLINK]
                                    self.prettyprint("sending packet "
                                                + packet_to_string(pkt)
                                                + " to output port " + str(out))
                                    self.outports[out].put(pkt)
                                else:
                                    # The packet violates a policy and must be dropped
                                    self.prettyprint("packet "
                                                + packet_to_string(pkt)
                                                + " has been " + Color.RED + "dropped: " + motivation + Color.ENDC)
                                    self.dropped.append((pkt, motivation))
                            else: # Link is momentarily inactive (dropped)
                                self.prettyprint("packet "
                                            + packet_to_string(pkt)
                                            + " has been " + Color.RED + "dropped for inactive link" + Color.ENDC)
                                self.dropped.append((pkt, "inactive link"))
                        except KeyError: # If a rule for this destination doesn't exist, ask it to the controller
                            self.inqueue.put((port, pkt))
                            self.controller_link.request(Strings.NEW_RULE, self.responses,
                                                         (destination, len(self.outports)))


        for lis in self.outports: # Signal the end of operations to all listening consumers
            lis.put("STOP")

        self.print_disc_pkts()