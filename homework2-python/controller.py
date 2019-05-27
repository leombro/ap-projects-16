from lib import *
from copy import deepcopy

class Controller():
    """
        A representation of the interface of a network's controller towards a switch.

        The simulation uses only one switch and thus only one instance of an object of this class.
        Generally, there should be one object for every switch in the network.
    """
    def __init__(self, network, optargs=None):
        """
            The constructor for objects of type Controller.

            network: A list of IP Addresses, belonging to terminals in the network.
            optargs: Optional arguments for the simulation (which rule to drop/delete and a redirection).
                     If those arguments are not provided, the simulation will use the following:
                     - rule to drop/delete: the first one in the (sorted) routing table's key set
                     - redirection: from/to addresses are respectively the first and the last ones in the routing
                       table's keyset
        """
        self.network = network
        self.requested_updates = -1 # Number of updates requested by the switch
        self.last_sent_ftab = {} # A copy of the last sent routing table
        self.filters = [] # A copy of currently active filters
        self.redirects = {} # A copy of currently active redirects
        if optargs:
            self.optargs = True
            self.todrop = optargs[0]
            self.todelete = optargs[1]
            self.redirect_from = optargs[2]
            self.redirect_to = optargs[3]
        else:
            self.optargs = False

    def request(self, type, resp_list, data=None):
        """
            Receives and processes requests from the switch.

            type: The type of the request (either NEW_TABLE, NEW_RULE or UPDATE).
            resp_list: Recipient for the controller's responses.
            data: Optional data for the request.
        """
        def prettyprint(message):
            """
                Utility method to print a message with a blue-colored "Controller: " prefix.

                message: The message to be printed.
            """
            print(Color.BLUE + Color.BOLD + "Controller: " + Color.ENDC + message)
        if type == Strings.NEW_TABLE:
            prettyprint("received a 'new table' request")
            ftab = {}
            install(list(range(data)), self.network, ftab) # A NEW_TABLE request is managed by an Install API call.
            self.last_sent_ftab = ftab
            resp_list.append({Strings.RESP_TYPE:Strings.NEW_TABLE, Strings.RESP_DATA:deepcopy(ftab)})
        elif type == Strings.NEW_RULE: # Switch doesn't have a rule for a specific destination in its routing table.
            which, ports = data
            prettyprint("received a 'new rule for destination " + str(which) + "' request")
            add_rule_for(which, list(range(ports)), self.last_sent_ftab)
            resp_list.append({Strings.RESP_TYPE:Strings.UPDATED_TABLE, Strings.RESP_DATA:deepcopy(self.last_sent_ftab)})
        elif type == Strings.UPDATE: # Switch requests an update. Controller tracks the number of updates.
            self.requested_updates += 1 # The simulation runs accordingly to the number of requested updates.
            prettyprint("received an 'update' request (#" + str(self.requested_updates) + ")")
            if self.requested_updates == 2: # The second update request causes a rule to be dropped.
                if not self.optargs:
                    self.todrop = sorted(list(self.last_sent_ftab))[0]
                drop(self.todrop, self.last_sent_ftab)
                resp_list.append({Strings.RESP_TYPE: Strings.UPDATED_TABLE, Strings.RESP_DATA:deepcopy(self.last_sent_ftab)})
            elif self.requested_updates == 4: # The fourth update request causes the dropped rule to be reactivated.
                if not self.optargs:
                    self.todrop = sorted(list(self.last_sent_ftab))[0]
                reactivate(self.todrop, self.last_sent_ftab)
                resp_list.append({Strings.RESP_TYPE: Strings.UPDATED_TABLE, Strings.RESP_DATA:deepcopy(self.last_sent_ftab)})
            elif self.requested_updates == 6: # The sixth update request causes the deletion of a rule.
                if not self.optargs:
                    self.todelete = sorted(list(self.last_sent_ftab))[0]
                delete(self.todelete, self.last_sent_ftab)
                resp_list.append({Strings.RESP_TYPE: Strings.UPDATED_TABLE, Strings.RESP_DATA:deepcopy(self.last_sent_ftab)})
            elif self.requested_updates == 8: # The eighth update request causes the activation of a security policy.
                tab = {}
                install_policy(tab, 3, 3) # The security policy is "Max 3 READs, max 3 WRITEs".
                resp_list.append({Strings.RESP_TYPE: Strings.POLICIES, Strings.RESP_DATA: deepcopy(tab)})
            elif self.requested_updates == 10: # The tenth update request causes the installation of a filter.
                def bad_function1(): # Three mock, unacceptable functions are created.
                    """This function has a wrong number of arguments"""
                    return 0
                def bad_function2(pkt):
                    """This function has a wrong return type"""
                    return 0
                def bad_function3(pkt):
                    """This function has a wrong argument type"""
                    return pkt.pop() == 0
                def good_function(pkt): # Only this function should be added to the filter list.
                    """Discards every packet whose destination port is lower than 450"""
                    return (pkt[Strings.PAYLOAD])[Strings.DEST_PORT] < 450
                if install_filter(self.filters, bad_function1):
                    print(Color.RED + "ERROR: INSERTED BAD FUNCTION 1")
                if install_filter(self.filters, bad_function2):
                    print(Color.RED + "ERROR: INSERTED BAD FUNCTION 2")
                if install_filter(self.filters, bad_function3):
                    print(Color.RED + "ERROR: INSERTED BAD FUNCTION 3")
                if not install_filter(self.filters, good_function):
                    print(Color.RED + "ERROR: GOOD FUNCTION NOT INSERTED")
                resp_list.append({Strings.RESP_TYPE: Strings.FILTERS, Strings.RESP_DATA: deepcopy(self.filters)})
            elif self.requested_updates == 12: # The twelfth update request causes the activation of a redirect.
                if not self.optargs:
                    addresses = sorted(list(self.last_sent_ftab))
                    self.redirect_from, self.redirect_to = addresses[0], addresses[len(addresses) - 1]
                mobility(self.redirects, self.redirect_from, self.redirect_to)
                resp_list.append({Strings.RESP_TYPE: Strings.MOBILITY, Strings.RESP_DATA: deepcopy(self.redirects)})
            elif self.requested_updates == 21: # The twenty-first update request stops the simulation.
                resp_list.append({Strings.RESP_TYPE:Strings.END, Strings.RESP_DATA:None})
            else: # Every other update request has no effect (NOP response).
                resp_list.append({Strings.RESP_TYPE: Strings.NOP, Strings.RESP_DATA: None})


