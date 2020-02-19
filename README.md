# jedi-time

![jedi-time-avatar](jedi.png)

A tiny Clojure library designed to _complement_ the `java.time` API (introduced with Java 8).
It exposes a handful of functions, and is mainly based on the `p/Datafiable` and `p/Navigable` protocols 
(introduced with Clojure 1.10).

## Where

[![Clojars Project](https://clojars.org/jedi-time/latest-version.svg)](https://clojars.org/jedi-time)

## Premise 
`java.time` is a piece of art. The API is well thought out, its classes are immutable, its methods well named, 
the conventions/idioms introduced are sound and are consistently followed, and generally speaking it is (for the most part)
 a joy to work with.

## Aspirations
 
 Given the above premise, `jedi-time` **doesn't** try to be a full wrapper around `java.time`. There are good Clojure wrappers around.
 Instead, the intention here to provide a bridge from `java.time` objects to Clojure maps (and back - even if 
 the metadata was somehow lost). Certain navigation facilities (as we'll see) do have a wrapper feel to them, 
 but they don't even scratch the surface of the entire `java.time` API.  

## Conventions/Semantics

### datafy
In its original conception `jedi-time` used extensive map nesting in order to mirror the hierarchical nature of temporal fields.
This kind of nesting wasn't playing very well with `nav`, and so the datafication model is now a bit flatter. Instead of nesting, a composite object
will contain more than one top-level keys. The simplest example to illustrate this is by comparing a `Year` VS a `Month` VS a `YearMonth`. 
In the original model (release 0.1.4 only), the latter would have a single `:year` key and a `[:year :month]` path, whereas now there will be
two top-level keys (`:year` and `:month`), whose combination represents the (datafied) `YearMonth`. Similarly, a datafied `LocalDateTime`
will contain two top-level keys (`:local-date` and `:local-time`). This facilitates intuitive/consistent navigation (see below).

### nav 
Navigation through _non-namespaced_ keys will lead you to `java.time` objects (that can be further datafied, navigated, and so on).
Navigation through _namespaced_ keys will lead you to base values (typically Number/String).
It's important to note that navigation through existing keys pays attention not only to the key being navigated (2nd arg), 
but also to the value (3rd arg). This basically means that navigating to an altered value will return an altered object, 
and so care needs to be taken when updating (or adding/removing for that matter) keys, as it does affect navigation. 

Finally, `jedi-time` adds some extra semantics for `:format`, `offset`, `:zone` and `:instant` navigation paths (explained later).
 
## Usage

### jedi-time.core

The majority of the functionality is provided in the core namespace. 
Loading/requiring it automatically extends `clojure.core.protocols/Datafiable` to the following 13 `java.time` types:

1. Month
2. MonthDay
3. DayOfWeek 
4. Year
5. YearMonth
6. LocalTime
7. LocalDate
8. LocalDateTime
9. OffsetDateTime
10. ZonedDateTime
11. Instant
12. ZoneOffset
13. ZoneId

All datafied implementations return a navigable (`clojure.core.protocols/Navigable`) map. 
You can think of it as a plain Clojure map if you only care about the raw data. 
However, navigation can take us places, so if you like travelling buckle on. ;) 

Ok, so you have a `java.time` object - what can you do with it? The obvious thing is to turn it into data 
(see the [intro](doc/intro.md) for an exhaustive list).

```clj
(require '[jedi-time.core :as jdt]
         '[clojure.datafy :as d])   ;; first things first

(d/datafy (jdt/now! {:as :zoned-datetime})) ;; datafy an instance of ZonedDateTime
=> 
{:local-date {:week-day #:day{:name "WEDNESDAY" 
                              :value 3}
              :month #:month{:name "FEBRUARY" 
                             :value 2 
                             :length 29 
                             :day 19}
              :year #:year{:value 2020 
                           :leap? true 
                           :length 366 
                           :week 8 
                           :day 50}}
 :local-time {:day/hour 16
              :hour/minute 32
              :minute/second 42
              :second/nano 428017000
              :second/milli 428
              :second/micro 428017}  ;; micro-precision on Java-9 and above
 :offset #:offset{:id "Z" 
                  :seconds 0 
                  :hours 0.0}
 :zone #:zone{:id "Europe/London"}}         

(class *1)
=> clojure.lang.PersistentArrayMap
```
This is the (richest possible) object represented as data. 
This alone, opens up a world of opportunities, but wait there is more...

Given the above data representation, we can navigate to a bunch of things 
(see the [intro](doc/intro.md) for an exhaustive list):

```clj
(let [datafied (d/datafy (jdt/now! {:as :zoned-datetime}))] 
  
  (d/nav datafied :format :iso)       ;; =>  "2020-01-29T08:37:31.737789Z[Europe/London]"
  (d/nav datafied :format "yy-MM-dd") ;; =>  "20-01-29"
  (d/nav datafied :instant nil)       ;; =>  #object[java.time.Instant 0x19ca0015 "2020-01-29T08:37:31.737789Z"]
 ) 
```

You can downgrade (by giving up some information), or upgrade (by making some assumptions) the datafied representation: 

```clj

(let [datafied (d/datafy (jdt/now! {:as :offset-datetime}))] 
  ;; downgrading (this works because the graph is traversed recursively)
  (d/nav datafied :local-datetime nil) ;; => #object[java.time.LocalDateTime 0x7363452f "2020-01-29T10:15:21.399461"]
  (d/nav datafied :local-date nil)     ;; => #object[java.time.LocalDate 0x167f1c41 "2020-01-29"]
  (d/nav datafied :year-month nil)     ;; => #object[java.time.YearMonth 0x9a9de2f "2020-01"]
  ;; ...etc
 )

(let [datafied (d/datafy (jdt/now! {:as :instant}))] 
  
  ;; upgrading (this works because Instant doesn't naturally have zone information)
  (d/nav datafied :zone "Europe/London")                         
  ;; => #object[java.time.ZonedDateTime 0x671baa90 "2020-02-11T21:34:55.558861Z[Europe/London]"]
  
  (d/nav (assoc datafied :zone "Europe/London") :zone nil)       ;; zone as String works     
  ;; => #object[java.time.ZonedDateTime 0x671baa90 "2020-02-11T21:34:55.558861Z[Europe/London]"]
  
  (d/nav (assoc datafied :zone {:zone/id "Europe/London"}) :zone nil) ;; zone as data works
  ;; => #object[java.time.ZonedDateTime 0x671baa90 "2020-02-11T21:34:55.558861Z[Europe/London]"]
 );; this is super useful for storing/commnicating essentially compressed zoned/offset-datetimes (an `Instant` carrying a `:zone`/`:offset`).  

(let [datafied (d/datafy (jdt/now! {:as :local-date}))]
  ;; upgrading (assuming start-of-day)
  (d/nav datafied :local-datetime nil)  ;; => #object[java.time.LocalDateTime 0x1a19c079 "2020-02-11T00:00"]
 )

```

This whole datafy/nav dance basically creates a graph structure.   

#### jedi-time.core/undatafy 

As the name implies, `jdt/undatafy` is the opposite of `d/datafy`.
It can take any Clojure map (not necessarily the direct result of `d/datafy`), 
and turn it into the correct `java.time` object. Therefore, you don't need to worry about losing 
the metadata carried by the result of `d/datafy`.

#### jedi-time.core/redatafy 

The composition of `jdt/undatafy` and `d/datafy`. Useful for re-obtaining `d/nav` capabilities on a meta-less mirror of something datafied.  

### jedi-time.datafied.specs
Self-explanatory package - start with `jedi-time.datafied.specs.core.clj`. Not connected to anything at runtime, but super useful for
development, documentation, debugging etc. Specs typically follow the requirements of `jdt/undatafy`. 
This means that if a map can be un-datafied correctly, then it also satisfies its spec (and vice-versa). 
So there are essentially two ways to confirm whether a map is correct datafied representation or not - 
a fully transparent one (the corresponding spec), and an opaque one (trying to `undatafy`).  

### jedi-time.datafied.tools
Top level namespace for interacting with datafied representations. 
All functions in this namespace accept a map as the 1st arg (something datafied), 
and return a new one. 

#### shift+/shift-
Takes a datafied representation and a vector of two elements (n, units), 
and returns a new datafied shifted forward by n units. An optional 3rd boolean arg 
will indicate whether the operation should be done safely (guarded by a `.isSupported()` condition),
in which case may return nil. 

```clj
(let [datafied (d/datafy (jdt/now! {:as :local-datetime}))]
  (get-in datafied [:local-date :week-day :day/value])        ;; => 3 
  (get-in datafied [:local-time :day/hour])                   ;; => 17
  
  (get-in (bt/shift+ datafied [4 :hours]) [:local-time :day/hour]) ;; => 21 (17 + 4)
  (get-in (bt/shift+ datafied [2 :days]) [:local-date :week-day :day/value]) ;; => 5 (3 + 2)
 ) ;; ;; care has been taken to use Period VS Duration correctly depending on the object/unit at hand.
``` 

Round-tripping using the same amount of time is a no-op (will take you back to the map you started with). 
Datafied representations of objects like `ZoneId` and `Offset` (obviously) don't support this. 

#### before?/after?
Predicates for chronologically comparing two datafied representations  
of the same type (this, other). 
Boils down to `this.isBefore(other)`, and `this.isAfter(other)`. Datafied representations 
of objects like `ZoneId`, `Offset`, `Month` and `DayOfWeek` don't support this.

#### same-instant?/date?
Predicates for instant/date equality comparison between two datafied representations
of **not** necessarily the same type.

```clj
(let [now-inst (d/datafy (jdt/now! {:as :instant}))
      now-date (d/datafy (jdt/now! {:as :local-date}))]

  (tools/same-instant? now-inst now-date)  ;; => false
  (tools/same-date?    now-inst now-date)  ;; => true
 )
```

#### at-zone/at-offset
For datafied representations that naturally come with a zone/offset, 
this will translate them to the provided zone/offset (2nd arg). 
The translation is done *with-same-instant* by default, but can be overridden in the 3rd arg.

For representations that don't naturally come with a zone/offset, this will attempt to upgrade them 
(potentially making some assumptions along the way).

As a side-note, this is entirely based on `d/nav`, so technically you don't need to go through these fns.
For instance, consider a datafied `ZonedDateTime` with an extra key:

```clj
(-> (ZonedDateTime/now (ZoneId/of "US/Pacific"))
    d/datafy
    (assoc-in [:zone :zone/id] "Europe/Athens") ;; update the zone
    (assoc-in [:zone :same] :instant)      ;; provide a tranlation context for navigating to the new zone
    (d/nav :zone nil) ;; in the context of REBL the last arg wouldn't be nil 
    d/datafy          ;; datafying a brand new ZonedDateTime object
    (d/nav :format :iso))  

=> "2020-02-12T12:57:10.229428+02:00[Europe/Athens]"

;; of course, this also works:

(-> (ZonedDateTime/now (ZoneId/of "US/Pacific"))
    d/datafy
    (d/nav :zone {:zone/id "Europe/Athens" :same :instant}) ;; the value we're providing doesn't have to match what's inside the map
    d/datafy
    (d/nav :format :iso))

=> "2020-02-12T23:03:16.398514+02:00[Europe/Athens]"

;; but this gives you back the ZoneId object

(-> (ZonedDateTime/now (ZoneId/of "US/Pacific"))
    d/datafy
    (d/nav :zone {:zone/id "Europe/Athens"})) ;; no :same key - no translation

=> #object[java.time.ZoneRegion 0x6c3eec0e "Europe/Athens"]

```
Hopefully the above illustrates that :zone/:offset have extra navigation capabilities, 
and these two helpers simply wrap those. 

### jedi-time.parse
This is a convenience namespace. It provides the following (fully) type-hinted parsing functions, 
each taking one (String) or two (String, DateTimeFormatter) args.

- parse-zoned-datetime
- parse-offset-datetime
- parse-local-datetime
- parse-local-date
- parse-local-time
- parse-year-month

## REBL friendly 
I am, by no means, a REBL-expert (only used it to test this project), but as far as I can tell datafied `java.time` 
representations browse/navigate as expected. If you are a seasoned REBL user and find that something doesn't work as  
you would expect, or is not intuitive, please do report it in an issue.   
  
My (potentially incomplete) understanding is that the `nav->` functionality only makes sense for keys that are present 
in the map, and therefore visible on the right hand side. Keys that don't exist (e.g. `:format`) still navigate to the 
corresponding thing, but any such key will always feel somewhat magical in the context of `REBL` (as it cannot be seen anywhere).

## Tips and tricks

### ZonableInstant
Such a class doesn't exist. You can have an `Instant` or a `ZonedDateTime`. 
However, there are cases when you have a point-in-time on one hand (e.g. when a user logged in),
and a zone on the other (the zone-id of the user). In some sense, you can keep those two separate
and combine them at the same time. Consider the following map:

```clj
{:second/nano ...
 :epoch/second ...
 :zone {:zone/id ...}} ;; this map-entry could come from anywhere and doesn't affect anything other than `nav`
```   
This is a perfectly valid `Instant` (i.e. it satisfies its spec and un-datafies correctly), 
but in terms of navigation it kind of resembles a `ZonedDateTime`. In fact, the `:zone` entry 
doesn't even need to be in the map, it can be passed as the 3rd arg to nav. 
You can do the same with `:offset`.  

### Randomly updating datafied representations 
Avoid doing this for any purpose other than navigation. 
Prefer shifting via the helpers in `jedi-time.datafied.tools.clj`.

### Traversing the graph
Sometimes it is possible to skip levels when navigating, and other times it isn't. 
For example, you can navigate to `:julian/day` directly from a datafied `ZonedDateTime`, 
but **not** from a datafied `Instant`. This is intentional, as an `Instant` requires zone/offset 
translation in order to obtain date field(s) awareness. So you must first upgrade it 
(see `tools/at-zone` and `tools/at-offset`), and then skipping levels becomes possible.

## TL;DR 

- `clojure.core/Datafiable` has been extended to the main 13 types in `java.time`.

- `(d/datafy x)` turns those 13 types into Clojure maps, and `jdt/undatafy` turns those (or meta-less mirrors of them) back into the right Java object.

- These maps can be navigated (via `d/nav`) in ways that reflect the inner structure of the underlying Java objects.
 
- These maps make themselves useful by wrapping some of functionality of the underlying Java objects (formatting, shifting, comparing etc). 

- Some extra navigation capabilities exist (beyond the contained keys). 

- Some parsing helpers exist in `jedi-time.parse`.

- Reflection-free

## License

Copyright © 2019 Dimitrios Piliouras

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
