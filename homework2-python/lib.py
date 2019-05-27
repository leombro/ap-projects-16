#
# Author: Orlando Leombruni
#
# This file contains the API for managing software-defined networks.
#

from common import *
from ipaddress import ip_address as ip

def __util(addr, ftab, val):
    """
        Common code for the drop and reactivate methods. ("Private" method)

        addr: The destination address of the rule to be changed.
        ftab: The routing table to be modified.
        val: The value to be set, either True or False
    """
    to_drop = ftab.get(addr)
    if to_drop:
        to_drop[Strings.ACTIVE] = val
    else:
        raise ValueError

def drop(addr, ftab):
    """
        Marks a rule as not active.

        addr: The destination address of the rule to drop.
        ftab: The routing table to be modified.
    """
    __util(addr, ftab, False)

def reactivate(addr, ftab):
    """
        Marks a rule as active.

        addr: The destination address of the rule to reactivate.
        ftab: The routing table to be modified.
    """
    __util(addr, ftab, True)


def install(interfaces, network, ftable):
    """
        Creates a routing table.

        The algorithm for constructing the routing table is a simple round-robin strategy. For every possible
        source address, it associates the destination at list position n to the output link at position (n mod m),
        where m is the number of output interfaces.

        interfaces: Identifiers of the output interfaces of the switch.
        network: A list of the IP addresses of the terminals in the network.
        ftable: The routing table to be modified.

        Raises IndexError if either the list of output interfaces or the list of IP addresses are empty.
    """
    ftable.clear()
    n = len(interfaces)
    if (n == 0 | len(network) == 0): raise IndexError
    i = 0
    for addr in network:
        ftable[addr] = {Strings.OUTLINK: interfaces[i], Strings.ACTIVE:True}
        i = i+1
        if i >= n:
            i = 0

def delete(addr, ftab):
    """
        Deletes a rule from a routing table.

        addr: The destination address of the rule to delete.
        ftab: The routing table to be modified.
    """
    del ftab[addr]

def add_rule_for(newaddr, interfaces, ftab):
    """
        Adds a rule to a routing table.

        newaddr: The destination address of the new rule.
        interfaces: The list of the output interfaces of the switch.
        ftab: The routing table to be modified.

        Raises IndexError if the list of output interfaces is empty.
    """
    if len(interfaces) == 0: raise IndexError
    ftab[newaddr] = {Strings.OUTLINK:interfaces[0], Strings.ACTIVE:True}

def install_policy(ptable, max_read, max_write):
    """
        Activates a new, global security policy.
        If a policy was already active, the new policy will overwrite it.

        ptable: A table that will contain the new policy.
        max_read: Number of admissible READs.
        max_write: Number of admissible WRITEs.
    """
    ptable.clear()
    ptable[Strings.POLICIES] = (max_read, max_write)

def install_filter(filtable, fun):
    """
        Installs a new filter.

        Filters must be Python functions from packets to booleans. Invalid filters
        will not be installed.

        filtable: A list, possibly empty, of already installed filters.
        fun: The function to be installed as a filter.
    """
    test_packet = {Strings.SRC_ADDR: ip('127.0.0.1'),
                   Strings.DST_ADDR: ip('127.0.0.1'),
                   Strings.PAYLOAD: {Strings.DEST_PORT: 80,
                                     Strings.OP: 'o'}}
    try:
        result = fun(test_packet)
        if isinstance(result, bool):
            filtable.insert(0, fun)
            return True
        else:
            return False
    except:
        return False

def mobility(rules, from_addr, to_addr):
    """
        Installs a redirect rule. Any packet directed to the old destination ("from_addr") will be rerouted to
        the new destination ("to_addr").

        rules: A list of redirect rules.
        from_addr: The destination's old IP address.
        to_addr: The destination's new IP address.
    """
    rules[from_addr] = to_addr