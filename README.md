# jedi-time

A tiny Clojure library designed to complement the `java.time` API (introduced with Java 8).
It exposes a handful of functions, and is mainly based on the `p/Datafiable` and `p/Navigable` protocols (introduced with Clojure 1.10).

## Premise 
`java.time` is a piece of art. Its API is well thought out, its classes are immutable, its methods are well named, 
the conventions/idioms introduced are sound and are consistently followed, and generally speaking it is, for the most part,
 a joy to work with. `JodaTime` is/was a pretty good library itself (and hugely popular), but was immediately superseded by `java.time` when 
 it was released - and that tells a lot.

## Aspirations
 
 Given the above premise, `jedi-time` **doesn't** try to be a wrapper around `java.time`. There are good Clojure wrappers around.
 Instead, the intention here to provide a bridge from java.time objects to Clojure maps (and back - even if the metadata was somehow lost).   

## Convention
Temporal fields are hierarchical, and so in Java the naming convention follows the `bar-of-foo` pattern (e.g ISO fields etc).
In `jedi-time` `bar-of-foo` becomes path `[:foo :bar]`. For instance `ChronoField/DAY_OF_YEAR` becomes `[:year :day]`, 
and you can use it to pull out the value from the datafied object using `get-in` (see below). 


## Usage

### jedi-time.core

The majority of the functionality is provided in the **`jedi-time.core`** namespace. 
Requiring it automatically extends the `clojure.protocols/Datafiable` protocol to the following 8 
`java.time` types:

1. Month 
2. YearMonth
3. LocalTime
4. LocalDate
5. LocalDateTime
6. OffsetDateTime
7. ZonedDateTime
8. Instant

All these `Datafiable` implementations return a `clojure.protocols/Navigable` map. 
You can think of it as a plain Clojure map if you only care about the raw data. 
However, navigation can take us places, so if you like travelling buckle on. ;) 

Ok, so you have a `java.time` object - what can you do with it? The obvious thing is to turn it into data.

```clj
(d/datafy (ZonedDateTime/now)) 
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
(let [datafied (d/datafy (ZonedDateTime/now))] 
  (d/nav datafied :format :iso)       =>  "2020-01-29T08:37:31.737789Z[Europe/London]"
  (d/nav datafied :format "yy-MM-dd") =>  "20-01-29"
  (d/nav datafied :instant nil)       =>  #object[java.time.Instant 0x19ca0015 "2020-01-29T08:37:31.737789Z"]
 
)
```
You can navigate to some (chronologically) modified, or alternate version (where supported).


```clj
(d/nav datafied :+ [3 :hours])
(d/nav datafied :- [2 :days]) 

This may promote the types
(d/nav datafied :at-zone   ["Europe/London" :same-instant])
(d/nav datafied :at-offset ["+01:00"        :same-local])  
```



You can navigate to any direct parents (e.g. :local-time + :local-date => local-datetime) 
of the datafied object, except from a datafied `Instant` which can be navigated to pretty much anything: 

```clj

(let [datafied (d/datafy (OffsetDateTime/now))] 
  (-> datafied  
     (d/nav :local-datetime nil) ;; => #object[java.time.LocalDateTime 0x7363452f "2020-01-29T10:15:21.399461"]
     d/datafy
     (d/nav :local-date nil)     ;; => #object[java.time.LocalDate 0x167f1c41 "2020-01-29"]
     d/datafy 
     (d/nav :year-month nil)     ;; => #object[java.time.YearMonth 0x9a9de2f "2020-01"]
    )
)

```

If calling `d/nav` returns a Java object, it is going to be one of those 8 objects, and so it can be datafied. 
This whole datafy/nav dance basically creates a graph structure.   


#### undatafy 

As the name implies `jedi.core/undatafy` is the opposite of `d/datafy`.
It can take any Clojure map (not necessarily the direct result of `d/datafy`), 
and turn it into the correct `java.time` object. Therefore, you don't need to worry about losing 
the metadata carried by the result of `d/datafy` (which conveniently includes the original object).


### jedi-time.parse
This is a convenience namespace. It provides the following (fully) type-hinted parsing functions, 
each taking one (String) or two (String, DateTimeFormatter) args.

- parse-zoned-datetime
- parse-offset-datetime
- parse-local-datetime
- parse-local-date
- parse-local-time
- parse-year-month

## License

Copyright © 2019 Dimitrios Piliouras

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
