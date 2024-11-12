# CMFTBL DSL (currently only MFOTL)

## Defining Formulas
Every formula must be defined inside
```
formula {
...
}
```
The formula can receive up to two "fixed" references, which denote a concrete object. This concrete objecte can be given with formula.holds()
```
val formula = formula { x: Ref<Vehicle> ->
...
}
formula.holds(makeFixedRef(Vehicle(id = 1)))
```


### Operators
- tt() - True
- ff() - False
- pred(...) - for access to domain
- neg - negation
- and (inline)
- or (inline)
- impl - Implication (inline)
- iff - If and only If (inline)

- prev - Previous
- next
- once
- historically
- eventually
- always - Globally
- since
- until

- forall
- exists



### Examples
```
val hasMidTrafficDensity = formula {
      forall() { x: Ref<Vehicle> ->
        eventually {
          (const(6) leq
              term { x.now().let { v -> v.tickData.vehiclesInBlock(v.lane.road.block).size } }) and
              (term { x.now().let { v -> v.tickData.vehiclesInBlock(v.lane.road.block).size } } leq
                  const(15))
        }
      }
    }
```
