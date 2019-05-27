import producer, consumer, switch, controller, ipaddress, sys, argparse, threading, time
from cmd import Cmd

class MyShell(Cmd):
    """
        A shell for the step-by-step mode. Supports command history and autocompletion.
    """
    def __init__(self, switch, ps, cs, efun):
        super().__init__()
        self.switch = switch
        self.producers = ps
        self.consumers = cs
        self.endfun = efun
        self.ended = False

    def emptyline(self):
        pass

    def postcmd(self, stop, line):
        time.sleep(0.5)
        if line == 'break': return True
        if not self.ended:
            if self.switch.end:
                self.ended = True
                return False
        else:
            return True

    def preloop(self):
        time.sleep(0.5)

    def postloop(self):
        self.endfun()
        print("Bye!")

    def do_step(self, arg):
        """Advances n steps (default: 1)."""
        if not arg:
            for p in self.producers:
                p.do_step()
        else:
            try:
                steps = int(arg)
                if steps <= 0: raise ValueError
                self.cmdqueue.extend(["step\n" for _ in range(steps)])
            except:
                print("Please insert a positive (integer) number of steps.")

    def do_skip(self, args):
        """Reverses to automatic mode."""
        for p in self.producers:
            p.step = False
            p.do_step()
        self.endfun()
        self.cmdqueue.extend([""])

    def do_table(self, args):
        """Prints the switch's routing table."""
        self.switch.print_ftab()

    def do_policies(self, args):
        """Prints the state of the currently-enforced policy."""
        self.switch.print_policies()

    def do_filters(self, args):
        """Prints the table of currently active filters."""
        self.switch.print_filters()

    def do_redirects(self, args):
        """Prints the table of currently active redirects."""
        self.switch.print_mobility()

    def do_packets(self, args):
        """Prints the list of consumed and discarded packets."""
        self.switch.print_disc_pkts()
        for c in self.consumers:
            c.print_rec_pkts()

    def do_break(self, args):
        """Ends the simulation right away."""
        self.switch.end = True
        for p in self.producers:
            p.stop()
        return True

def main():
    """
        The simulation logic. Two producers, five consumers, a switch and a controller are set up, and the producers
        start generating packets and putting them into the switch's input queue. The simulation runs for about 20
        packets per producer (around 40 packets in total) and shows every API functionality.

        The simulation can run in either one of two modes:

        - Automatic (default). The two producers act autonomously; after every sent packet, they sleep for 2
          seconds and then reprise their execution. The second producer is delayed (with respect to the first one)
          for 1 second. The sleep and delay values can be adjusted using the "--time slp dly" command-line option.
        - Manual (or step-by-step). The two producers wait for user input after every sent packet. The user, through
          a simple shell, can either advance the computation, stop it at any moment, resume the automatic mode, or
          check the current status (routing table, filters, policies, redirects, processed packets). This mode can
          be activated through the "--step" command-line argument.
    """
    parser = argparse.ArgumentParser(description='A simulation of the behaviour of the network',
                                     epilog='Please note that the "--time" argument has no effect '
                                            'in step-by-step mode.')
    parser.add_argument('--step', action='store_true', help='runs the simulation in step-by-step mode')
    parser.add_argument('--time', nargs=2, metavar=('slp', 'dly'), help='sets to slp the time between two packets '
                                    'from the same producer, and to dly the delay between the two producers.\n'
                                    'The default values are slp=2s, dly=1s', type=float)
    args = parser.parse_args()
    step = args.step

    if not step:
        if not args.time:
            sleep, delay = 2, 1
        else:
            sleep, delay = args.time[0], args.time[1]
    else:
        sleep, delay = 0.1, 0.1

    range1start = int(ipaddress.ip_address('89.5.8.1'))
    range1end = int(ipaddress.ip_address('89.5.8.12'))
    range2start = int(ipaddress.ip_address('92.22.7.1'))
    range2end = int(ipaddress.ip_address('92.22.7.5'))
    range3start = int(ipaddress.ip_address('89.5.8.1'))
    range3end = int(ipaddress.ip_address('89.5.8.5'))
    netw1 = [ipaddress.ip_address(addr) for addr in range(range1start, range1end)]
    netw2 = list(reversed([ipaddress.ip_address(addr) for addr in range(range3start, range3end)]))
    sources1 = [ipaddress.ip_address(addr) for addr in range(range2start, range2end)]

    contr = controller.Controller(netw1)
    sw = switch.Switch(contr, 5)
    prod1 = producer.Producer(0, sw, sources1, netw1, list(range(80, 1024, 30)), step=step, sleeptime=sleep, delay=0)
    prod2 = producer.Producer(1, sw, sources1, netw2, list(range(80, 1024, 30)), step=step, sleeptime=sleep, delay=delay)
    cons1 = consumer.Consumer(sw.outports[0], 0)
    cons2 = consumer.Consumer(sw.outports[1], 1)
    cons3 = consumer.Consumer(sw.outports[2], 2)
    cons4 = consumer.Consumer(sw.outports[3], 3)
    cons5 = consumer.Consumer(sw.outports[4], 4)
    sw_thread = threading.Thread(target=sw.start)

    def end():
        sw_thread.join()
        cons1.join()
        cons2.join()
        cons3.join()
        cons4.join()
        cons5.join()
        prod1.stop()
        prod2.stop()
        prod1.join()
        prod2.join()
        print("All done!")
        return 0

    cons1.name = "consumatore1"
    cons2.name = "consumatore2"
    cons3.name = "consumatore3"
    cons4.name = "consumatore4"
    cons5.name = "consumatore5"

    prod1.start()
    prod2.start()
    cons1.start()
    cons2.start()
    cons3.start()
    cons4.start()
    cons5.start()

    sw_thread.start()

    def pprint(msg):
        sys.stdout.write(msg)

    if step:
        command = MyShell(sw, [prod1, prod2], [cons1, cons2, cons3, cons4, cons5], end)
        command.prompt = '> '
        command.cmdloop("Commencing step-by-step mode...")
        return 0
    else:
        return end()



if __name__ == "__main__":
    sys.exit(main())

