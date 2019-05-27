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

type expr =
    | Filter of (packet -> bool)
  	| Interface of int list
  	| Network of int list
  	| Rule of int * int * int * bool * int * int
  	| RoutingTable of expr list
  	| GetRules of int * expr * expr
  	| GetRulesWithSP of int * expr * expr * (int * int)
  	| Install of expr * expr
    | InstallWithSP of int * int * expr * expr
  	| Drop of expr * expr
  	| Undrop of expr * expr
  	| Delete of expr * expr
  	| Redirect of int * int * expr

val eval : expr -> expr