open Dsl
open Network

let output_interfaces = [(1,[]);(2,[]);(3,[]);(4,[]);(5,[])]
let update_network() = network := [1;2;3;4;5;6;7];;

(* 
	The routing table is the following (disregarding the source address):
	(destination -> link)
	(1 -> 1)	(6 -> 1)
	(2 -> 2)	(7 -> 2)
	(3 -> 3)
	(4 -> 4)
	(5 -> 5)
*)

(* some payloads *)
let pl = { prt = Tcp; src_port = 4; dest_port = 25; op = Read; payload = "GET" }
let pl1 = { prt = Tcp; src_port = 4; dest_port = 5; op = Write; payload = "HELO" }
let pl2 = { prt = Udp; src_port = 2; dest_port = 5; op = Write; payload = "POST" }
let pl3 = { prt = Udp; src_port = 3; dest_port = 5; op = Write; payload = "PUT" }

(* 21 packets; they will be consumed in this order *)
let pkt1 = { name = "pkt1"; header = {src = 1; dest = 3}; payload = pl } (* it will be routed to link 3 *)
let pkt2 = { name = "pkt2"; header = {src = 3; dest = 7}; payload = pl } (* to link 2 *)
let pkt3 = { name = "pkt3"; header = {src = 7; dest = 4}; payload = pl } (* to link 4 *)
let pkt4 = { name = "pkt4"; header = {src = 1; dest = 2}; payload = pl } (* to link 2 *)
(* after processing pkt4, the switch requests the 
	first update, which drops (disables) the rule (source 1, destination 1, output port 1) *)
let pkt5 = { name = "pkt5"; header = {src = 1; dest = 1}; payload = pl } (* what a coincidence! This will be dropped *)
let pkt6 = { name = "pkt6"; header = {src = 4; dest = 6}; payload = pl } (* to link 1 *)
let pkt7 = { name = "pkt7"; header = {src = 4; dest = 3}; payload = pl } (* to link 3 *)
let pkt8 = { name = "pkt8"; header = {src = 4; dest = 2}; payload = pl } (* to link 2 *)
(* at this point, the switch requests a second update, 
	which deletes the rule (source 1, destination 1, output port 1) *)
let pkt9 = { name = "pkt9"; header = {src = 4; dest = 1}; payload = pl } (* to link 1 *)
let pkt10 = { name = "pkt10"; header = {src = 4; dest = 7}; payload = pl } (* to link 2 *)
let pkt11 = { name = "pkt11"; header = {src = 2; dest = 5}; payload = pl } (* to link 5 *)
let pkt12 = { name = "pkt12"; header = {src = 1; dest = 1}; payload = pl } (* no entry in table! *)
(* since there's no rule for (src 1, dst 1), the switch makes a request to the controller.
 Before it can process the response from the controller, though, a second (scheduled update) 
 request is sent to the controller. This second request brings a new routing table, 
 with enforced security policy "max 3 READs, any number of WRITEs". Packet 12 is now sent correctly,
 decreasing the number of remaining READs from source 1 to destination 1 *)
let pkt13 = { name = "pkt13"; header = {src = 1; dest = 1}; payload = pl } (* to link 1; second READ *)
let pkt14 = { name = "pkt14"; header = {src = 1; dest = 1}; payload = pl } (* to link 1; third READ *)
let pkt15 = { name = "pkt15"; header = {src = 1; dest = 1}; payload = pl } (* dropped because of security policy *)
(* the fourth update is requested; the filter "drop packets to transport port 2" is now active *)
let pkt16 = { name = "pkt16"; header = {src = 1; dest = 1}; payload = pl1 } (* to link 1; not dropped, this is a WRITE *)
let pkt17 = { name = "pkt17"; header = {src = 4; dest = 6}; payload = pl } (* to link 1 *)
let pkt18 = { name = "pkt18"; header = {src = 4; dest = 6}; payload = pl2 } (* dropped because of filters *)
let pkt19 = { name = "pkt19"; header = {src = 4; dest = 6}; payload = pl2 } (* dropped because of filters *)
(* fifth update requested: this redirects packets destined to address 3 towards the port for address 4 *)
let pkt20 = { name = "pkt20"; header = {src = 4; dest = 6}; payload = pl2 } (* dropped because of filters *)
let pkt21 = { name = "pkt21"; header = {src = 4; dest = 3}; payload = pl3 } (* should go into link 3, it goes into link 4 because of mobility *)
let pkt22 = { name = "pkt22"; header = {src = 1; dest = 5}; payload = pl3 } (* to link 5 *)
let pkt23 = { name = "pkt23"; header = {src = 7; dest = 4}; payload = pl3 } (* to link 4 *)
(* sixth update requested. The controller orders a shutdown, and the next packets are not processed *)
let pkt24 = { name = "pkt24"; header = {src = 4; dest = 5}; payload = pl3 } (* not processed *)
let pkt25 = { name = "pkt25"; header = {src = 1; dest = 3}; payload = pl3 } (* not processed *)

let input_interfaces = [(1, [pkt1; pkt4; pkt7; pkt10; pkt13; pkt16; pkt19; pkt22; pkt25]);  
						(2, [pkt2; pkt5; pkt8; pkt11; pkt14; pkt17; pkt20; pkt23]);  
						(3, [pkt3; pkt6; pkt9; pkt12; pkt15; pkt18; pkt21; pkt24])]

(* the output interfaces after the call to the switch function should be the following: *)
let control_ouif = [(1, [pkt6; pkt9; pkt12; pkt13; pkt14; pkt16; pkt17]); 
					(2, [pkt2; pkt4; pkt8; pkt10]); 
					(3, [pkt1; pkt7]); 
					(4, [pkt3; pkt21; pkt23]); 
					(5, [pkt11; pkt22])]

let () = update_network();;

(* simulation *)
let result = switch input_interfaces output_interfaces

(* check if the result is the desired one *)
let () = assert(result = control_ouif)