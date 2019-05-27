open Dsl

(* Switch<->controller data structures / types *)

(* Forwarding rule *)
type forwarding_rule = {
	src: int;
	dst: int;
	out: int;
	active: bool;
	mutable n_read: int;
	mutable n_write: int;
}

(* Interfaces identifiers *)
type interface = int

(* Controller responses *)
type controller_data =
	| ForwardingTable of forwarding_rule list
	| IncrementalUpdate of forwarding_rule list
	| Modified of forwarding_rule list
	| Redirected of forwarding_rule list
	| AddFilter of (packet -> bool)
	| End

(* Switch requests *)
type req_type = GetNewTable | GetRuleFor of int | Update
type request = {
	kind: req_type;
	outports: interface list;
}

(* Results for the forward operation *)
type 'a result = NoRes | Fault of int | Ok of 'a

(* Results for the search in the routing table *)
type ft_result = NotFound | Inactive | Result of int

type port = interface * (packet list)

(* Internal state of the controller *)

(* Internal representation of the network *)
let network : int list ref = ref []
(* Number of requested updates, useful for the simulation *)
let controller_state : int ref = ref 0 
(* Currently enforced policies (-2,-2 means no policy) *)
let controller_policies : (int*int) ref = ref (-2,-2) 
(* Last routing table sent by the controller to the switch *)
let controller_last_rt : expr list ref = ref [] 

(* 
	FUNCTION controller_interface: Interface between the controller and the switch

	IN: 
		request req - a single request from the switch
	OUT: 
		controller_data list - a (possibly empty) list of responses from the controller to the switch
	SIDE EFFECTS:
		updates the state of the controller (controller_state, controller_policies, controller_last_rt)
*)
let controller_interface (req: request) : controller_data list =
	(*
		FUNCTION unpack: converts a routing table from the DSL notation to the c/s notation

		IN:
			expr list rt - the routing table to convert
			forwarding_rule list acc - an accumulator
		OUT:
			forwarding_rule list - the converted routing table
		FAILURES:
			rt contains an element which is different from a rule - "Invalid RoutingTable"
	*)
	let rec unpack (rt: expr list) (acc: forwarding_rule list) 
												: forwarding_rule list =
		match rt with
		| [] -> acc
		| (Rule (s, d, o, a, r, w))::xs -> 
			unpack xs 
				(acc@[{src=s; dst=d; out=o; active=a; n_read=r; n_write=w}])
		| (_)::xs -> failwith "Invalid RoutingTable"

	in match req.kind with
	| GetNewTable -> 	(* the switch requested a new table *)
			 (match eval (Install(Interface(req.outports), Network(!network))) with
			 	(* the controller (via the eval) returned a routing table: store it, then forward it to the switch *)
			 | RoutingTable rt -> 
			 	(controller_last_rt := rt; 
			 	[ForwardingTable(unpack !controller_last_rt [])])
			 | _ -> failwith "Nonconforming result from interpreter"
			 )
	| GetRuleFor(i) -> (
			(* if i is not in the network, add it *)
			if (List.mem i !network) then ()
			else network := i::(!network);
			(* check if there are security policies enforced *)
			let expression = 
				if !controller_policies = (-2,-2) then 
					GetRules(i, Interface(req.outports), Network(!network))
				else 
					GetRulesWithSP(i, Interface(req.outports), 
									Network(!network), !controller_policies)
			in (match eval (expression) with
			 | RoutingTable rt -> 
			 	(controller_last_rt := rt@(!controller_last_rt); 
			 	[IncrementalUpdate(unpack !controller_last_rt [])])
			 | _ -> failwith "Nonconforming result from interpreter"
			 )
		)
	| Update ->
			(* this is the core of the simulation *)
			(* track the number of requested updates *)
			controller_state := !controller_state + 1;
			Printf.printf "Update requested! state = %d\n" !controller_state;
			let contr = !controller_state
			(* after 32 requests, stop *)
			in if contr = 6 then [End]
			else
				(* all operations are on the first rule, for simplicity *)
				let firstrule : expr = match !controller_last_rt with
				| [] -> failwith "Empty rule"
				| (Rule(_,_,_,_,_,_) as rul)::xs -> rul
				| _::_ -> failwith "Not a forwarding table"
				in if contr = 1 then ( (* Drop the first rule *)
					match eval (Drop(firstrule, RoutingTable(!controller_last_rt))) with
					| RoutingTable rt -> (controller_last_rt := rt; [Modified(unpack !controller_last_rt [])])
					| _ -> failwith "Nonconforming result from interpreter"
				)
				else if contr = 2 then ( (* Delete the first rule *)
					match eval (Delete(firstrule, RoutingTable(!controller_last_rt))) with
					| RoutingTable rt -> (controller_last_rt := rt; [Modified(unpack !controller_last_rt [])])
					| _ -> failwith "Nonconforming result from interpreter"
				)
				else if contr = 3 then ( (* enforce the security policy "max 3 read, infinite writes" *)
					controller_policies := (3, -2); 
					match eval (InstallWithSP(3, -2, Interface(req.outports), Network(!network))) with
					| RoutingTable rt -> (controller_last_rt := rt; [ForwardingTable(unpack !controller_last_rt [])])
					| _ -> failwith "Nonconforming result from interpreter"
				)
				else if contr = 4 then ( (* add a filter *)
					match eval (Filter((fun p -> p.payload.src_port = 2))) with
					| Filter f -> [AddFilter (f)]
					| _ -> failwith "Nonconforming result from interpreter"
				) 
				else if contr = 5 then ( (* apply a redirect *)
					match eval (Redirect(3, 4, RoutingTable(!controller_last_rt))) with
					| RoutingTable rt -> (controller_last_rt := rt; [Redirected(unpack !controller_last_rt [])])
					| _ -> failwith "Nonconforming result from interpreter"
				)
				else []

(*
	FUNCTION switch: simulation of a network switch

	IN:
		port list in_if - input interfaces of the switch, initially loaded with all the packets of the simulation
		port list out_if - output interfaces of the switch, initially empty
	OUT:
		port list - the output interfaces, with every packet correctly routed
*)
let switch (in_if: port list) (out_if: port list) : port list =
	let outports = List.map fst out_if
	in
		(*
			FUNCTION apply_edits: applies edits to a routing table, maintaining the states of the security policy

			IN:
				forwarding_rule list ftab_old - the routing table to be edited
				forwarding_rule list edits - the edits to be merged
				forwarding_rule list acc - an accumulator
			OUT:
				forwarding_rule list - the modified routing table

			COMMENTS: both ftab_old and edits are traversed in parallel; if the rules match 
			(same source, destination, output link and active state), the old one is retained.
			If the rules differ only for their "active" field, then the new one is taken.
			If the rules differ for other fields, that mean that the old one has been deleted.
			FAILURES:
				the new table has more entries than the old -> "It was an IncrementalUpdate"
		*)
		let rec apply_edits (ftab_old: forwarding_rule list) (edits: forwarding_rule list) 
					(acc: forwarding_rule list) : forwarding_rule list =
			match (ftab_old, edits) with
			| (_, []) -> acc
			| ([], _) -> failwith "It was an IncrementalUpdate"
			| (x::xs, ((y::ys) as t)) -> 
				if x.src = y.src && x.dst = y.dst && x.out = y.out then (* if Drop: replace rule *)
					apply_edits xs ys (if x.active = y.active then acc@[x] else (acc@[y]))  
				else apply_edits xs t acc (* Delete: skip rule *)
		(*
			FUNCTION apply_redirect: merges edits made by a Redirect update to a forwarding table

			IN:
				forwarding_rule list ftab_old - the routing table to be edited
				forwarding_rule list edits - the edits to be merged
				forwarding_rule list acc - an accumulator
			OUT:
				forwarding_rule list - the modified routing table

			COMMENTS: both ftab_old and edits are traversed in parallel; if the rules match 
			(same source and destination, output link and active state), the old one is retained.
			If the rules differ only for their "output link" field, then the new one is taken.
			If the rules differ for other fields, that's an error.
			FAILURES:
				the old table has more entries than the new one -> "It was a Modified"
				the new table has more entries than the old one -> "It was an IncrementalUpdate"
				big mismatch on traversing the two lists -> "This is not a redirect update"
		*)
		in let rec apply_redirect (ftab_old: forwarding_rule list) (edits: forwarding_rule list) 
					(acc: forwarding_rule list) : forwarding_rule list =
			match (ftab_old, edits) with
			| ([], []) -> acc
			| ([], _) -> failwith "It was an IncrementalUpdate"
			| (_, []) -> failwith "It was a Modified"
			| (x::xs, y::ys) -> 
				if x.src = y.src && x.dst = y.dst && x.active = y.active then
					apply_redirect xs ys (if x.out = y.out then acc@[x] else (acc@[y]))  
				else failwith "This is not a redirect update" (* mismatch! *)
		(* 
			FUNCTION update: updates the routing table and the filter list according to the responses received from the controller

			IN:
				forwarding_rule list ftab - the old routing table
				controller_data list resp - responses received from the controller
				(packet -> bool) filters - the old list of filters
			OUT:
				forwarding_rule list * (packet -> bool) list - the new routing table and list of filters
		*)
		in let rec update (ftab: forwarding_rule list) 
			(resp: controller_data list) (filters: (packet -> bool) list): forwarding_rule list * (packet -> bool) list =
			match resp with
			| [] -> (ftab, filters)
			| x::xs -> (
				match x with
				| ForwardingTable ft -> update ft xs filters
				| IncrementalUpdate iu -> update (iu@ftab) xs filters
				| Modified m -> update (apply_edits ftab m []) xs filters
				| Redirected m -> update (apply_redirect ftab m []) xs filters
				| AddFilter f -> update ftab xs (f::filters)
				| End -> ([], [])
			)
		(*
			FUNCTION enforce_sp: checks whether a security policy causes a packet to drop

			IN:
				forwarding_rule r - the rule containing the state of the security policy
				transport_op opt - the operation of the packet
			OUT:
				int - the output link on where the packet must be routed, or -1 if the packet must be dropped
			SIDE EFFECTS:
				updates the state of the security policy
		*)
		in let enforce_sp (r: forwarding_rule) (opt: transport_op) : int =
			match opt with
			| Other -> r.out
			| Read -> let nread = r.n_read 
					  in if (nread <= 0) then 
					  	if (nread = -2) then r.out else -1
					  else (r.n_read <- nread - 1; r.out)
			| Write -> let nwrite = r.n_write
					   in if (nwrite <= 0) then 
					   	if (nwrite = -2) then r.out else -1
					   else (r.n_write <- nwrite - 1; r.out)
		(*
			FUNCTION find_rule: checks whether the routing table contains a rule for forwarding a packet

			IN:
				forwarding_rule list tab - the routing table
				int source - the source address of the packet
				int dest - the destination address
				transport_op opt - the operation of the packet
			OUT:
				ft_result - the result of the rule search:
							- NotFound, if there isn't any rule corresponding to source/dest
							- Inactive, if there's a rule but the packet must be dropped (security policy or rule dropped)
							- Result n, if the packet must be forwarded on link n according to a rule
		*)
		in let rec find_rule (tab: forwarding_rule list) (source: int) 
							(dest: int) (opt: transport_op) : ft_result =
			match tab with
			| [] -> NotFound
			| r::rs when (r.src = source && r.dst = dest) -> 
					if (r.active = false) then Inactive
					else if r.n_read = -2 && r.n_write = -2 then Result(r.out)
					else let res = enforce_sp r opt in
					if res < 0 then Inactive else Result(res)
			| r::rs -> find_rule rs source dest opt
		(*
			FUNCTION forward: puts a packet into the correct output link

			IN:
				packet p - the packet to be forwarded
				int interf - the output link obtained from the forwarding rule
				port list out_ps - list of output interfaces
			OUT:
				port list - list of output interfaces, possibly updated with a new packet
				in the correct link
			FAILURES:
				empty output interfaces list (or end of recursion): "Cannot find output interface"
		*)
		in let rec forward (p: packet) (interf: int) (out_ps: port list) : port list =
			match out_ps with
			| [] -> failwith "Cannot find output interface"
			| o::os -> let (port, pkts) = o in
					   if port = interf then (port, pkts@[p])::os
					   else o::(forward p interf os)
		(*
			FUNCTION send: searches into the routing table for a rule and executes the forward function

			IN:
				packet pkt - the packet to be sent
				forwarding_rule list tab - the routing table
				port list out_ps - list of output interfaces
			OUT:
				port list result - either Fault(n), where n is the destination of pkt,
				if the routing table doesn't have any rule for n, or Ok(outs), where
				outs is the list of output interfaces (possibly augmented with pkt in the
				correct interface, if it's not dropped)
		*)
		in let rec send (pkt: packet) (tab: forwarding_rule list) 
									(out_ps: port list) : port list result =
			match find_rule tab pkt.header.src pkt.header.dest pkt.payload.op with
			| NotFound -> Fault(pkt.header.dest)
			| Inactive -> (Printf.printf "Dropped packet %s!\n" pkt.name; Ok(out_ps))
			| Result(out_port) -> Ok(forward pkt out_port out_ps)
		(*
			FUNCTION check_filters: checks if a packet is to be dropped according to a filter

			IN:
				filter list fil - the list of filters
				packet pkt - the packet to be checked
			OUT:
				bool - true if the packet is to be sent, false if it's to be dropped
		*)
		in let rec check_filters (fil: (packet -> bool) list) (pkt: packet) : bool =
			match fil with
			| [] -> true
			| f::fs -> 	let dropped = f pkt
						in if dropped then false else check_filters fs pkt
		(*
			FUNCTION send_packet: finds a packet to be routed and performs all necessary operations

			IN:
				port list inifs - the list of input interfaces
				port list outifs - the list of output interfaces
				forwarding_rule list ftab - the routing table
				(packet -> bool) list filters - the list of filters to be applied
			OUT:
				(port list * port list) result - either NoRes, if there are no
				more packets to forward; Fault(i), if the routing table has no
				rule for destination i; Ok(in,out), where in is the list of input interfaces
				and out the list of output interfaces, possibly modified to remove a packet
				from one of the input interfaces to the correct (according to a forwarding rule)
				output interface
			FAILURES:
				"Incorrect send result" if the result of the send function is NoRes, which is
				not a possible outcome
		*)
		in let rec send_packet (inifs: port list) (outifs: port list) 
			(ftab: forwarding_rule list) (filters: (packet -> bool) list) : ((port list * port list) result) =
			match inifs with
			| [] -> NoRes
			| ((id, pkts) as i)::is -> (
				match pkts with
				| [] -> (
					match (send_packet is outifs ftab filters) with 
					| NoRes -> 	NoRes
					| (Fault _) as r -> r
					| Ok(ini, outi) -> Ok(i::ini, outi)
				  )
				| p::ps -> (
					if (check_filters filters p) then
						(match send p ftab outifs with
						| NoRes -> failwith "Incorrect send result"
						| Fault(i) -> Fault(i)
						| Ok(outi) -> Ok(is@[(id, ps)], outi))
					else (Printf.printf "Filtered packet %s!\n" p.name; Ok(is@[(id, ps)], outifs))
				  )
			  )
		(*
			FUNCTION sw_aux: a single computation cycle of the switch unit

			IN:
				int iterations - the number of already-done iterations 
				port list inp - list of input interfaces
				port list outp - list of output interfaces
				forwarding_rule list f_tab - the routing table
				filter list filters - list of filters
				controller_data list cdata - list of responses from the controller
			OUT:
				port list - at the end of the whole computation, the output interfaces
				contain all of the correctly-routed packets, and are returned

			COMMENTS:
				for simulation purposes, the function counts the number of iterations in order
				to request a controller update every five iterations. In every other iteration,
				the routing table and filter list are updated with information obtained from the
				controller; if the routing table is empty after the update, the function ends.
				Otherwise, it processes a packet and either forwards it to the correct link or
				asks the controller for a new rule if its destination has not a rule in the routing
				table.
		*)
		in let rec sw_aux (iterations: int) (inp: port list) (outp: port list) 
						  (f_tab: forwarding_rule list) (filters: (packet -> bool) list) (cdata: controller_data list) : port list =
			if (iterations mod 5 = 0) then
				let updates = controller_interface({kind = Update; outports = outports})
				in sw_aux (iterations+1) inp outp f_tab filters (cdata@updates)
			else
				let (newtable, newfilters) = update f_tab cdata filters (* all cdata is used here *)
	    		in if newtable = [] then outp
				else
					match (send_packet inp outp newtable filters) with
					| NoRes -> outp
					| Fault(i) -> let response =
										controller_interface {kind=GetRuleFor(i); outports=outports}
									   in sw_aux (iterations + 1) inp outp newtable newfilters response
					| Ok(a,b) -> sw_aux (iterations + 1) a b newtable newfilters []

in
	let (first_table, first_filters) = update [] (controller_interface ({kind = GetNewTable; outports = outports})) []
	in sw_aux 1 in_if out_if first_table first_filters []
