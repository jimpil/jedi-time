# jedi-time

A tiny Clojure library designed to complement the `java.time` API (introduced with Java 8).
It exposes a handful of functions, and is mainly based on the `p/Datafiable` and `p/Navigable` protocols 
(introduced with Clojure 1.10, _and mirrored in this library_).

## Premise 
`java.time` is a piece of art. Its API is well thought out, its classes are immutable, its methods are well named, 
the conventions/idioms introduced are sound and are consistently followed, and generally speaking it is (for the most part)
 a joy to work with. `JodaTime` is/was a superb library itself (and hugely popular), but was immediately superseded by `java.time` when 
 the latter was released - and that says a lot.

## Aspirations
 
 Given the above premise, `jedi-time` **doesn't** try to be a wrapper around `java.time`. There are good Clojure wrappers around.
 Instead, the intention here to provide a bridge from `java.time` objects to Clojure maps (and back - even if 
 the metadata was somehow lost). Navigation facilities (as you'll see below) do have a wrapper feel to them, but they don't even scratch the surface of the `java.time` API.  

## Convention
Temporal fields are hierarchical, and so in Java the naming convention follows the `bar-of-foo` pattern (e.g ISO fields etc).
In `jedi-time` `bar-of-foo` becomes path `[:foo :bar]`. For instance `ChronoField/DAY_OF_YEAR` becomes `[:year :day]`, 
and you can use it to pull out the value from the datafied object using `get-in` (see below). 

## Usage

### jedi-time.core

The majority of the functionality is provided in the **`jedi-time.core`** namespace. 
Loading/requiring it automatically extends the `jedi-time.protocols/Datafiable` protocol to the following 9 
`java.time` types:

1. Month
2. DayOfWeek 
3. YearMonth
4. LocalTime
5. LocalDate
6. LocalDateTime
7. OffsetDateTime
8. ZonedDateTime
9. Instant

All `Datafiable` implementations return a `jedi-tim.protocols/Navigable` map. 
You can think of it as a plain Clojure map if you only care about the raw data. 
However, navigation can take us places, so if you like travelling buckle on. ;) 

Ok, so you have a `java.time` object - what can you do with it? The obvious thing is to turn it into data.

```clj
;; first things first
(require '[jedi-time.core :as jdt])

;; datafy an instance of ZonedDateTime
(jdt/datafy (jdt/now! :as :zoned-datetime)) 
=> 
{:day  {:hour 20},
 :hour {:minute 9},
 :week {:day {:name "WEDNESDAY",
              :value 3}},
 :second {:nano 11914000,
          :milli 11,
          :micro 11914},
 :offset {:id "Z",
          :hours 0,
          :seconds 0},
 :zone {:id "Europe/London"},
 :year {:month {:name "JANUARY",
                :value 1,
                :length 31,
                :day 8},
       :length 366,
       :leap? true,
       :value 2020,
       :day 8
       :week 2},
 :minute {:second 34}}

(class *1)
=> clojure.lang.PersistentArrayMap
```
This is the object represented as data. 
This alone, opens up a world of opportunities, but wait there is more...

Given the above data representation, we can navigate to a bunch of things 
(see INTRO.MD for an exhaustive list):

```clj
(let [datafied (jdt/datafy (jdt/now! :as :zoned-datetime))] 
  (jdt/nav datafied :format :iso)       =>  "2020-01-29T08:37:31.737789Z[Europe/London]"
  (jdt/nav datafied :format "yy-MM-dd") =>  "20-01-29"
  (jdt/nav datafied :instant nil)       =>  #object[java.time.Instant 0x19ca0015 "2020-01-29T08:37:31.737789Z"]
 
)
```
You can navigate to some (chronologically) modified, or alternate version (where supported).

```clj
(jdt/nav datafied :+ [3 :hours])
(jdt/nav datafied :- [2 :days]) ;; will correctly use Period (as opposed to Duration)

;;this may promote the types
(jdt/nav datafied :at-zone   ["Europe/London" :same-instant])
(jdt/nav datafied :at-offset ["+01:00"        :same-local])  
```

You can navigate to any direct parents (e.g. :local-time + :local-date => local-datetime) 
of the datafied object, except from a datafied `Instant` which can be navigated to pretty much anything: 

```clj

(let [datafied (jdt/datafy (jdt/now! :as :offset-datetime))] 
  (-> datafied  
     (jdt/nav :local-datetime nil) ;; => #object[java.time.LocalDateTime 0x7363452f "2020-01-29T10:15:21.399461"]
     jdt/datafy
     (jdt/nav :local-date nil)     ;; => #object[java.time.LocalDate 0x167f1c41 "2020-01-29"]
     jdt/datafy 
     (jdt/nav :year-month nil)     ;; => #object[java.time.YearMonth 0x9a9de2f "2020-01"]
    )
)

```

If calling `jdt/nav` returns a Java object, it is going to be one of the aforementioned 9 objects, and so it can be datafied. 
This whole datafy/nav dance basically creates a graph structure.   


#### undatafy 

As the name implies `jedi-time.core/undatafy` is the opposite of `jedi-time.core/datafy`.
It can take any Clojure map (not necessarily the direct result of `jedi-time.core/datafy`), 
and turn it into the correct `java.time` object. Therefore, you don't need to worry about losing 
the metadata carried by the result of `datafy` (which conveniently includes the original object).


### jedi-time.parse
This is a convenience namespace. It provides the following (fully) type-hinted parsing functions, 
each taking one (String) or two (String, DateTimeFormatter) args.

- parse-zoned-datetime
- parse-offset-datetime
- parse-local-datetime
- parse-local-date
- parse-local-time
- parse-year-month

## TL;DR 

- I don't own any of the protocols in `clojure.core.protocols`, nor any of the `java.time` types, so both `Datafiable` 
and `Navigable` have been mirrored in `jedi-time.protocols`.

- These protocols have been extended to the main 9 types in `java.time`.

- `(jdt/datafy x)` turns those 9 types into Clojure maps.

- These maps can be navigated with similar semantics as the corresponding Java objects (via `(jdt/nav m ...)`).  

- Some parsing helpers exist in `jedi-time.parse`.

## License

Copyright © 2019 Dimitrios Piliouras

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
