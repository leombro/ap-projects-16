type filter_type = DropTCP | DropUDP | DropFromSrcPort of int

type expr =
	| Filter of filter_type
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