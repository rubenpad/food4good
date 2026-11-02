# food4good

A restaurant's free-food donation event: the Manager places Orders, the Kitchen prepares a randomly chosen Dish for each, and Food Storage supplies the ingredients, buying from the Farmers Market when it runs short.

## Language

### Ordering

**Manager**:
The person running the donation event who places Orders. There is a single Manager role; anyone using the system acts as the Manager.
_Avoid_: User, admin, customer

**Order**:
A Manager's request to the Kitchen for one Dish. An Order is never cancelled or failed; once accepted it waits as long as it takes to be delivered.
_Avoid_: Pedido, ticket, request

**Order status**:
An accepted Order is **waiting for ingredients** (its Reservation is being filled), then **ready** (Reservation complete, waiting for a Cook), then **preparing** (a Cook is on it), then **delivered**.
_Avoid_: Pending, in progress, done

**Dish**:
What the Kitchen prepares and delivers for an Order: one instance of a randomly chosen Recipe.
_Avoid_: Plate, meal, plato

**Maximum wait**:
The longest estimated time to clear the Backlog the Kitchen will tolerate; Orders that would push the Backlog beyond it are not accepted.
_Avoid_: Timeout, SLA

### Kitchen

**Kitchen**:
Receives Orders, assigns each a Recipe at random, obtains its ingredients from Food Storage, and has a Cook prepare and deliver the Dish.

**Recipe**:
One of a fixed set of six preparations: the Ingredients it needs, the quantity of each, and its preparation time. Every Ingredient appears in at least one Recipe.
_Avoid_: Menu item, dish (a Dish is an instance; a Recipe is the template)

**Cook**:
A unit of Kitchen capacity that prepares one Dish at a time, taking the Recipe's preparation time. The Kitchen has a configured number of Cooks.
_Avoid_: Worker, station, chef

**Backlog**:
The preparation work of every accepted Order not yet delivered, expressed as an estimated time to clear given the number of Cooks.
_Avoid_: Queue (the backlog is measured in time, not count)

### Food Storage

**Ingredient**:
One of the ten raw foods a Recipe can use: tomato, lemon, potato, rice, ketchup, lettuce, onion, cheese, meat, chicken. Counted in whole units.

**Food Storage**:
Holds the stock of each Ingredient (5 units each at the start) and fulfils Ingredient Requests, buying whatever it lacks from the Farmers Market.
_Avoid_: Warehouse, inventory, bodega, pantry

**Ingredient Request**:
The Kitchen asking Food Storage for every Ingredient one Order's Recipe needs, in the quantities it needs.
_Avoid_: Ingredient order (an Order is only ever the Manager's)

**Reservation**:
The units Food Storage sets aside for one Ingredient Request so no other Order can take them, filled first from available units and then from Purchases made for that request. It never expires; it is consumed when a Cook starts preparing the Dish.
_Avoid_: Hold, allocation, lock

**On hand / Reserved / Available**:
Per Ingredient: **on hand** is every unit in Food Storage; **reserved** is the part held by Reservations; **available** is on hand minus reserved — what a new Ingredient Request can still take.

### Farmers Market

**Farmers Market**:
The external seller Food Storage buys missing Ingredients from. It may sell fewer units than asked for, or none at all.
_Avoid_: Marketplace, plaza, supplier

**Purchase**:
A successful buy from the Farmers Market: one Ingredient and the non-zero number of units sold. Attempts that sell nothing are not Purchases. Units bought beyond what the triggering Reservation needs become available to everyone.
_Avoid_: Buy attempt, transaction
