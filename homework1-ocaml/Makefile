OCB_FLAGS 	= -I src -I src/lib
OCB 		= ocamlbuild $(OCB_FLAGS)

all:	native byte lib

clean:
	$(OCB) -clean

lib:
	$(OCB) libdsl.cma
	$(OCB) libdsl.cmxa
	$(OCB) libdsl.cmxs
	mv ./_build/src/lib ./lib

native:
	$(OCB) test.native

byte:
	$(OCB) test.byte



test:	native
	./test.native

.PHONY:	all clean lib native byte test