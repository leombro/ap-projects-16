class Color():
    """
        Color codes for nice visual effects on the terminal's output
    """
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    PINK = '\033[95m'
    RED = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'

class Strings():
    """
        Strings used as keys for various dictionaries.
    """
    SRC_ADDR = 'SRC_ADDR'
    DST_ADDR = 'DST_ADDR'
    OUTLINK = 'OUTPUT_LINK'
    ACTIVE = 'ACTIVE'

    NEW_TABLE = 'NEW_TABLE'
    UPDATED_TABLE = 'UPDATED_TABLE'
    NEW_RULE = 'NEW_RULE'
    END = 'END'
    UPDATE = 'UPDATE'
    NOP = 'NO_OP'
    RESP_TYPE = 'RESP_TYPE'
    RESP_DATA = 'RESP_DATA'
    PAYLOAD = 'PAYLOAD'
    DEST_PORT = 'DEST_PORT'
    POLICIES = 'POLICIES'
    OP = 'OP'
    FILTERS = 'FILTERS'
    MOBILITY = 'MOBILITY'

def compare(rule1, rule2):
    """
        Compares two rules, ignoring the validity bit.

        rule1: The first rule to compare.
        rule2: The second rule to compare.
        returns: True if the rules are equal (ignoring the validity bit), False otherwise.
    """
    return (rule1[Strings.DST_ADDR] == rule2[Strings.DST_ADDR]) & (rule1[Strings.OUTLINK] == rule2[Strings.OUTLINK])

def packet_to_string(pkt):
    """
        Returns the textual representation for a packet.

        pkt: The packet.
        return: A textual representation for pkt.
    """
    return "{" + \
           Color.BOLD + "DEST: " + Color.ENDC + str(pkt[Strings.DST_ADDR]) + \
           Color.BOLD + " SRC: " + Color.ENDC + str(pkt[Strings.SRC_ADDR]) + \
           Color.BOLD + " PAYLOAD: " + Color.ENDC + str(pkt[Strings.PAYLOAD]) + "}"

