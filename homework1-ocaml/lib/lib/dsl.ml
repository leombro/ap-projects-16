(* Packet types *)
type protocol = Tcp | Udp

type transport_op = Read | Write | Other

type segment = {
  prt: protocol;
  src_port: int;
  dest_port: int;
  op: transport_op;
  payload: string;
}

type ip_header = {
  src: int;
  dest: int;
}

type packet = {
  name: string;
  header: ip_header;
  payload: segment;
}
(* Constructs of the language, refer to the documentation for further details *)
type expr =
	| Filter of (packet -> bool) (* a single filter *)
  | Interface of int list (* a list of interface identifiers *)
  | Network of int list (* a list of IP addresses *)
  | Rule of int * int * int * bool * int * int (* A rule: source, destination, out_link, active, n_of_read, n_of_write *)
  | RoutingTable of expr list (* a routing table *)
  | GetRules of int * expr * expr (* request a rule for a specific destination address *)
  | GetRulesWithSP of int * expr * expr * (int * int) (* request a rule for a specific destination address, with security policies *)
  | Install of expr * expr (* get a new routing table without security policies *)
	| InstallWithSP of int * int * expr * expr (* get a new routing table with the security policy "max n read, m write" 
  (n is the first int, m is the second int) *)
  | Drop of expr * expr (* drops a rule *)
  | Undrop of expr * expr (* reactivates a rule *)
  | Delete of expr * expr (* deletes a rule *)
  | Redirect of int * int * expr (* modifies a table to forward packets into the correct port of the new destination *)

(*
  FUNCTION eval: the interpreter of the language

  IN:
    expr expression - the expression to be evaluated
  OUT:
    expr - the result of the evaluation, according to the semantics of the language
*)
let rec eval (expression: expr) : expr =

  (*
    FUNCTION check: typechecks a rule list
    
    IN:
      expr list t - list to be checked
    OUT:
      expr list - the same list
    FAILURES:
      "A routing table can only be comprised of forwarding rules", if the function
      encounters an element which is not a rule
  *)
  let rec check (t: expr list) : expr list =
    match t with
    | [] -> []
    | e::es -> (
  	      match e with
  	      | Rule _ as t1 -> t1::(check es)
  	      | _ -> failwith "A routing table can only be comprised of forwarding rules"
      )
  (*
    FUNCTION make_rt: creates a list of forwarding rules, possibly with security policies

    IN:
      int list net - all the possible IP addresses
      int list netb - a second copy of net
      int list outl - the identifiers of the output interfaces of the switch
      expr list acc - an accumulator
      int no_r - the number of admissible READs
      int no_w - the number of admissible WRITEs
    OUT:
      expr list - a list of forwarding rules
    COMMENTS:
      the algorithm for constructing the routing table is a simple round-robin strategy. For every possible
      source address, it associates the destination at list position n to the output link at position (n mod m),
      where m is the number of output interfaces.
  *)
  in let rec make_rt (net: int list) (netb: int list) (outl: int list) (acc: expr list) (no_r: int) (no_w: int) : expr list =
    (*
      FUNCTION make_rt_aux: having fixed a single source address, it computes the rules for all possible destinations

      IN:
        int fix - the fixed source
        int list backup - a copy of the list of the output interfaces (for the round robin policy)
        int list curr - the list of output interfaces
        int list netw - the list of all possible destinations
        exp list acc1 - an accumulator
      OUT:
        expr list - a list of rules from a single source to all destinations
    *)
    let rec make_rt_aux (fix: int) (backup: int list) (curr: int list) (netw: int list) (acc1: expr list) : expr list =
      match netw with
      | [] -> acc1
      | (n::ns) as l -> (
          match curr with
          | [] -> make_rt_aux fix backup backup l acc1
          | o::os -> make_rt_aux fix backup os ns (acc1@[Rule(fix, n, o, true, no_r, no_w)])
        )
    in
      match net with
      | [] -> acc
      | n::ns -> make_rt ns netb outl (acc@(make_rt_aux n outl outl netb [])) no_r no_w
  (*
    FUNCTION dropfrom: switches a single rule of a routing table from "active" to "inactive"

    IN:
      expr list rt - a list of rules
      int src - the "source" value of the rule to be dropped
      int dst - the "destination" value of the rule to be dropped
      int out - the "output link" value of the rule to be dropped
    OUT:
      expr list - the same list of rule as the input, but with a specific rule dropped
    FAILURES:
      if the specified rule is not in the list -> "This rule is not in the forwarding table!"
      if the expr list has an element which is not a rule -> "Nonconforming routing table"
  *)
  in let rec dropfrom (rt: expr list) (src: int) (dst: int) (out: int) : expr list =
    match rt with
    | [] -> failwith "This rule is not in the forwarding table!"
    | (Rule(s,d,o,f,r,w) as rule)::xs ->
      if s = src && d = dst && o = out then
        if f = false then rt else Rule(s,d,o,false,r,w)::xs
      else rule::(dropfrom xs src dst out)
    | _ -> failwith "Nonconforming routing table"
  (*
    FUNCTION undropfrom: switches a single rule of a routing table from "inactive" to "active"

    IN:
      expr list rt - a list of rules
      int src - the "source" value of the rule to be activated
      int dst - the "destination" value of the rule to be activated
      int out - the "output link" value of the rule to be activated
    OUT:
      expr list - the same list of rule as the input, but with a specific rule activated
    FAILURES:
      if the specified rule is not in the list -> "This rule is not in the forwarding table!"
      if the expr list has an element which is not a rule -> "Nonconforming routing table"
  *)
  in let rec undropfrom (rt: expr list) (src: int) (dst: int) (out: int) : expr list =
    match rt with
    | [] -> failwith "This rule is not in the forwarding table!"
    | (Rule(s,d,o,f,r,w) as rule)::xs ->
      if s = src && d = dst && o = out then
        if f = true then rt else Rule(s,d,o,true,r,w)::xs
      else rule::(dropfrom xs src dst out)
    | _ -> failwith "Nonconforming routing table"

  (*
    FUNCTION delfrom: deletes a single rule from a routing table

    IN:
      expr list rt - a list of rules
      int src - the "source" value of the rule to be deleted
      int dst - the "destination" value of the rule to be deleted
      int out - the "output link" value of the rule to be deleted
    OUT:
      expr list - the same list of rule as the input, minus a specific rule
    FAILURES:
      if the specified rule is not in the list -> "This rule is not in the forwarding table!"
      if the expr list has an element which is not a rule -> "Nonconforming routing table"
  *)
  in let rec delfrom (rt: expr list) (src: int) (dst: int) (out: int) : expr list =
    match rt with
    | [] -> []
    | (Rule(s,d,o,_,_,_) as rule)::xs ->
      if s = src && d = dst && o = out then delfrom xs src dst out
      else rule::(delfrom xs src dst out)
    | _ -> failwith "Nonconforming routing table"
  (*
    FUNCTION redirect: modifies a routing table to forward packets directed to old_dest to the port of new_dest

    IN:
      expr list rt - the routing table to be modified
      int f - the old destination address
      int t - the new destination address
    OUT:
      expr list - the modified routing table
    FAILURES:
      if the expr list has an element which is not a rule -> "Nonconforming routing table"
      if the routing table is empty, or the recursion has ended without finding a value -> "This destination is not present in this routing table"
  *)
  in let redirect (rt: expr list) (f: int) (t: int) : expr list =
    let rec find_port rtab dest =
      match rtab with
      | [] -> failwith "This destination is not present in this routing table"
      | (Rule(_,d,o,_,_,_))::xs -> if d = dest then o else find_port xs dest
      | _ -> failwith "Nonconforming routing table"
    in let rec red_aux rt f port =
      (match rt with
      | [] -> []
      | (Rule(s,d,o,a,r,w) as rule)::xs -> ((if d = f then Rule(s,d,port,a,r,w) else rule)::(red_aux xs f port))
      | _ -> failwith "Nonconforming routing table")
    in red_aux rt f (find_port rt t)

  in match expression with
  | Interface _ | Network _ | Rule _ | Filter _  as e -> e (* "base cases" of the language: are evaluated to themselves *)
  | RoutingTable rt -> RoutingTable(check rt) (* applies a sort of "type checking" to the elements of the rt *)
  | GetRules (which, e1, e2) -> (* the set of rules for a single destination is computed with the standard make_rt *)
    (match (eval e1, eval e2) with (* this has the 'unfortunate' effect to always forward the new destination to the first link *)
    | (Interface i, Network n) -> RoutingTable(make_rt [which] n i [] (-2) (-2))
    | _ -> failwith "Malformed install request")
  | GetRulesWithSP (which, e1, e2, (r, w)) -> 
    (match (eval e1, eval e2) with
    | (Interface i, Network n) -> RoutingTable(make_rt [which] n i [] r w)
    | _ -> failwith "Malformed install request")
  | Install (e1, e2) ->
    (match (eval e1, eval e2) with
    | (Interface i, Network n) -> RoutingTable(make_rt n n i [] (-2) (-2))
    | _ -> failwith "Malformed install request")
  | InstallWithSP (r, w, e1, e2) ->
    (match (eval e1, eval e2) with
    | (Interface i, Network n) -> RoutingTable(make_rt n n i [] r w)
    | _ -> failwith "Malformed install request")
  | Drop (e1, e2) ->
      (match (eval e1, eval e2) with
        | (Rule(s,d,o,_,_,_), RoutingTable(rt)) -> RoutingTable(dropfrom rt s d o)
        | (_,_) -> failwith "Malformed drop request")
  | Undrop (e1, e2) ->
      (match (eval e1, eval e2) with
        | (Rule(s,d,o,_,_,_), RoutingTable(rt)) -> RoutingTable(undropfrom rt s d o)
        | (_,_) -> failwith "Malformed drop request")
  | Delete (e1, e2) ->
      (match (eval e1, eval e2) with
        | (Rule(s,d,o,_,_,_), RoutingTable(rt)) -> RoutingTable(delfrom rt s d o)
        | (_,_) -> failwith "Malformed del request")
  | Redirect (f, t, e) ->
      (match (eval e) with
      | RoutingTable rt -> RoutingTable(redirect rt f t)
      | _ -> failwith "Malformed del request")