# Introduction to jedi-time

We will look at each `java.time` class separately, showing what map it datafies to, and _where_ that map can be navigated to (next section). Then we wil look at _how_ to achieve that (the last argument to `nav`) per key.

## datafy and nav keys

The full set of `nav` keys supported are the following ():

- :format 
- :before? 
- :after?
- :to
- :julian
- :+ 
- :-

### Month 
This one is probably the least rich type. It datafies to the following map, which doesn't navigate to anything.

```clj
(d/datafy (Month/of 2))
=>
{:month {:name "FEBRUARY", 
         :value 2, 
         :length 28}} ;; length of a plain month isn't necessarily correct (leap-year precision is impossible) 
```

### DayOfWeek 
Similar to `Month`, this class datafies to the following map and doesn't navigate to anything. 

```clj
(d/datafy (DayOfWeek/of 2))
=> 
{:week {:day {:name "TUESDAY", 
              :value 2}}}

```

### YearMonth
Datafies the `Month` object found inside this (see above), updates its `:length` to the correct value 
(given the year), and wraps it with a map containing the `:year` details.

```clj
(d/datafy (YearMonth/of 2020 2))
=>
{:year 
  {:month {:name "FEBRUARY", 
           :value 2, 
           :length 29}, ;; length of a year-month is correct 
  :length 366, 
  :leap? true, 
  :value 2020}}
```
The returned map (representing a `YearMonth`) can now be navigated to the following keys: 

- :format 
- :before? 
- :after?
- :to
- :at-zone
- :at-offset
- :+ 
- :-

### LocalTime

Returns a map with 4 keys, each mapping to their immediate denominations, in order to form nesting levels 
of the form "hour-of-day", "minute-of-hour" etc.

```clj
(d/datafy (LocalTime/now)) ;; "14:16:10.001534"
=> 
{:day    {:hour 14}, 
 :hour   {:minute 16}, 
 :minute {:second 10}, 
 :second {:nano 1534000, 
          :milli 1, 
          :micro 1534}}
```
The returned map (representing a `LocalTime`) can now be navigated to the following keys: 

- :format 
- :before? 
- :after?
- :+ 
- :-

### LocalDate

Datafies the `YearMonth` and `DayOfWeek` objects found-in/constructed-from this, and merges the results. 

```clj
(d/datafy (LocalDate/now)) ;; "2020-01-31"
=> 
{:week {:day {:name "FRIDAY", 
              :value 5}},
 :year {:month {:name "JANUARY", 
                :value 1, 
                :length 31, 
                :day 31}, 
       :length 366, 
       :leap? true, 
       :value 2020, 
       :week 5}}

```
The returned map (representing a `LocalDate`) can now be navigated to the full set of `nav` 
keys mentioned earlier, **except**  `:at-zone` and `:at-offset`. 


### LocalDateTime

```clj
(d/datafy (LocalDateTime/now)) ;; "2020-01-31T14:47:13.453314"
=> 
{:day    {:hour 14},
 :hour   {:minute 47},
 :minute {:second 13},
 :second {:nano 453314000, 
          :milli 453, 
          :micro 453314},
 :week {:day {:name "FRIDAY", 
              :value 5}},
 :year {:month {:name "JANUARY", 
                :value 1, 
                :length 31, 
                :day 31},
        :length 366,
        :leap? true,
        :value 2020,
        :week 5,
        :day 31}}
```
The returned map (representing a `LocalDateTime`) can now be navigated to the full set of `nav` 
keys mentioned earlier, **except**  `:at-zone` and `:at-offset`. 


### OffsetDateTime
Constructs a LocalDateTime from this, datafies it (see above), and adds offset information.

```clj
(d/datafy (OffsetDateTime/now)) ;; "2020-01-31T14:59:19.243826Z"
=> 
{:day    {:hour 14},
 :hour   {:minute 59},
 :minute {:second 19},
 :second {:nano 243826000, :milli 243, :micro 243826},
 :week {:day {:name "FRIDAY", 
              :value 5}},
 :year {:month {:name "JANUARY", 
                :value 1, 
                :length 31, 
                :day 31},
        :length 366,
        :leap? true,
        :value 2020,
        :week 5,
        :day 31},
 :offset {:id "Z", 
          :seconds 0, 
          :hours 0.0}}
```
The returned map (representing an `OffsetDateTime`) can now be navigated to the full set of `nav` 
keys mentioned earlier. 

### ZonedDateTime
Same as `OffsetDateTime`, but adds a `:zone` key.

```clj
(d/datafy (ZonedDateTime/now)) ;; "2020-01-31T15:05:22.129798Z[Europe/London]"
=> 
{:day    {:hour 15},
 :hour   {:minute 5},
 :minute {:second 22},
 :second {:nano 129798000, 
          :milli 129, 
          :micro 129798},
 :week {:day {:name "FRIDAY", 
              :value 5}},
 :year {:month {:name "JANUARY", 
                :value 1, 
                :length 31, 
                :day 31},
        :length 366,
        :leap? true,
        :value 2020,
        :week 5,
        :day 31},
 :offset {:id "Z", 
          :seconds 0, 
          :hours 0.0},
 :zone {:id "Europe/London"}}
```
The returned map (representing an `ZonedDateTime`) can now be navigated to the full set of `nav` 
keys mentioned earlier. 


### Instant
Datafies to a map of two keys - `:epoch` and `:second`.

```clj
(d/datafy (Instant/now))
=>
{:second {:nano 609043000}, ;; nano of second
 :epoch {:second 1580483274, 
         :milli 1580483274609, 
         :micro 1580483274609043, 
         :nano 1580483274609043000}}
 
```
The returned map (representing an `Instant`) can now be navigated to the full set of `nav` 
keys mentioned earlier, **except**  `:at-zone`, `:at-offset` and `:julian`. 

## nav values

This section documents the last argument to `nav` (given that the first two args were explained in the previous section).

### :format (returns a String)

- anything other than a datafied `YearMonth` - the keyword `:iso` (default), a `DateTimeFormatter` instance, or a `String` pattern 
- a datafied `YearMonth` - a `DateTimeFormatter` instance, or a `String` pattern (defaults to "yyyy-MM")

### :before?/:after? (return true/false)

A datafied instance of the same or similar type (e.g. `ZonedDateTime` vs `OffsetDateTime`).

### :at-zone

An instance of `ZoneId`, or a String describing one (defaults to system zone).

### :at-offset

An instance of `ZoneOffset`, or a String describing one (defaults to system offset).

### :julian (returns Julian fields) 
One of the following keywords:

- :day 
- :modified-day
- :rata-die

### :+/:- (returns a shifted version of the same type)
A two-element vector. First element is expected to be a positive integer, followed by one of the following keywords:

- :nanos
- :micros
- :millis  
- :seconds 
- :minutes 
- :hours   
- :days    
- :half-days
- :weeks 
- :months 
- :years  
- :decades 
- :centuries
- :millenia 
- :eras 

### :to


