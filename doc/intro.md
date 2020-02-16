# Introduction to jedi-time

We will look at each `java.time` class separately, showing its data representation, and _where_ that can be navigated to. 


## datafy/nav


### Year
It datafies to the following map, which doesn't navigate to anything other than the top-level key (`:year`).

```clj
(d/datafy (Year/of 2020))
=>
{:year {:value 2020 
        :length 366
        :leap? true}}
```

### Month 
It datafies to the following map, which doesn't navigate to anything other than the top-level key (`:month`).

```clj
(d/datafy (Month/of 2))
=>
{:month {:name "FEBRUARY" 
         :value 2 
         ;; length of a plain month isn't necessarily correct 
         :length 28}} 
```

### DayOfWeek 
Similarly to `Month`, this class datafies to the following map and doesn't navigate to anything 
other than the top-level key (`:week-day`).

```clj
(d/datafy (DayOfWeek/of 2))
=> 
{:week-day {:name "TUESDAY" 
            :value 2}}

```

### YearMonth
Datafies the `Year`/`Month` objects and merges them, while correcting the `[:month :length]`.

```clj
(d/datafy (YearMonth/of 2020 2))
=>
{:year {:length 366 
        :leap? true 
        :value 2020}
 :month {:name "FEBRUARY" 
         :value 2 
         ;; length of month is correct here
         :length 29}} 
```
The returned map can be navigated to its top-level keys (returning `Year` and `Month` respectively), 
but also to the following extra keys: 

- :format - returns a String per `v` (the formatter pattern - defaults to `"yyyy-MM"` as there is no ISO variant for this)
- :year-month - returns itself as an object (`v` is ignored)
- :instant - returns an `Instant` at the start of the first day of this month in this year (`v` is ignored)
- :local-date - returns a `LocalDate` at the first day of this month in this year (`v` is ignored)
- :local-datetime - returns an `LocalDateTime` at the start of the first day of this month in this year (`v` is ignored)
- :offset - returns an `OffsetDateTime` at the start of the first day of this month in this year, per `v` (the offset data)
- :zone - returns an `ZonedDateTime` at the start of the first day of this month in this year, per `v` (the zone data)

As you can see, most of these are upgrade paths, and there are assumption(s) baked-in. 
Moreover, the last two (`:zone` and `:offset`) are arguably not useful, and even potentially confusing, 
but they are there to complete the graph. Be cautious and aware... 

### LocalTime

Datafies to the following (flat) map:

```clj
(d/datafy (LocalTime/now)) ;; "14:40:45.652059"
=> 
{:day/hour 14 
 :hour/minute 40 
 :minute/second 45 
 :second/nano 652059000 
 :second/milli 652 
 :second/micro 652059}
```
The returned map can be navigated to its top-level keys (returning numbers), 
but also to the following extra keys: 

- :format - returns a String per `v` (the formatter pattern - defaults to `"ISO_LOCAL_TIME`)
- :local-time - returns itself as an object (`v` is ignored)

### LocalDate

Datafies the `YearMonth` and `DayOfWeek` objects, and merges the results, 
while adding `:year/day`, and `:year/week`

```clj
(d/datafy (LocalDate/now)) ;; "2020-02-16"
=> 
{:week-day {:name "SUNDAY" 
            :value 7}
 :month {:name "FEBRUARY"
         :value 2 
         :length 29}
 :year {:value 2020 
        :leap? true 
        :length 366}
 :month-day {:value 16}
 :year/week 7
 :year/day 47}
```
The returned map can be navigated to its top-level keys (returning objects or numbers),
 but also to the following extra keys:
 
- :format - returns a String per `v` (the formatter pattern - defaults to `ISO_LOCAL_DATE`)
- :instant - returns an `Instant` at the start of this day, per `v` (the offset data)
- :local-date - returns itself as an object (`v` is ignored)
- :local-datetime - returns an `LocalDateTime` at the start of this day (`v` is ignored)
- :offset - returns an `OffsetDateTime` at the start of this day, per `v` (the offset data)
- :zone - returns an `ZonedDateTime` at the start of this day, per `v` (the zone data)
- :year-month - returns a `YearMonth` object (`v` is ignored) - this is a downgrade!
- :julian/day - returns the `JULIAN_DAY` (`v` is ignored)
- :julian/modified-day - returns the `MODIFIED_JULIAN_DAY` (`v` is ignored)
- :julian/rata-die - returns the `RATA_DIE` (`v` is ignored)

### LocalDateTime
Datafies the `LocalDate` and `LocalTime` objects and merges them.

```clj
(d/datafy (LocalDateTime/now)) ;; "2020-02-16T14:57:37.770679"
=> 
{:month {:name "FEBRUARY" 
         :value 2
         :length 29}
 :year {:value 2020 
        :leap? true 
        :length 366}
 :week-day {:name "SUNDAY" 
            :value 7}
 :month-day {:value 16}
 :hour/minute 57
 :minute/second 37
 :second/micro 770679
 :second/nano 770679000
 :second/milli 770
 :day/hour 14
 :year/day 47
 :year/week 7}
```
The returned map can be navigated to its top-level keys, 
but also to the following extra keys: 

- :format - returns a String per `v` (the formatter pattern - defaults to `ISO_LOCAL_DATE_TIME`)
- :instant - returns an `Instant` per `v` (the offset data)
- :local-date - returns an `LocalDate` object - this is a downgrade!
- :local-datetime - returns itself as an object (`v` is ignored) 
- :offset - returns an `OffsetDateTime` per `v` (the offset data)
- :zone - returns an `ZonedDateTime` per `v` (the zone data)

In addition, any non-overlapping keys at _lower_ levels (e.g. local-date/time) are also reachable from here.
For example, all the `:julian/*` keys from `LocalDate`, or the individual time fields from `LocalTime` (e.g. `:day/hour`). 

### OffsetDateTime
Constructs a `LocalDateTime` from this, datafies it (see above), and merges offset information (see below).

```clj
{:offset {:id "Z" 
          :seconds 0 
          :hours 0.0}}
```
The returned map can be navigated to its top-level keys, 
but also to the following extra keys:

- :format - returns a String per `v` (the formatter pattern - defaults to `ISO_OFFSET_DATE_TIME`)
- :instant - returns an `Instant` (`v` is ignored)
- :offset-datetime - returns itself as an object (`v` is ignored) 
- :local-datetime - returns a `LocalDateTime` object - this is a downgrade!
- :offset - translates this per `v` (the offset data) - returns `OffsetDateTime`
- :zone - translates this per `v` (the zone data) - returns `ZonedDateTime`

In addition, any non-overlapping keys at _lower_ levels (e.g. local-date/time) are also reachable from here.
For example, all the `:julian/*` keys from `LocalDate`, or the individual time fields from `LocalTime` (e.g. `:day/hour`). 

### ZonedDateTime
Same as `OffsetDateTime` (see above), this time adding a `:zone` key too (see below).

```clj
{:zone {:id "Europe/London"}}
```

The returned map can be navigated to its top-level keys, 
but also to the following extra keys:

- :format - returns a String per `v` (the formatter pattern - defaults to `ISO_ZONED_DATE_TIME`)
- :instant - returns an `Instant` (`v` is ignored)
- :offset-datetime - returns itself as an object (`v` is ignored) 
- :local-datetime - returns a `LocalDateTime` object - this is a downgrade!
- :offset - translates this per `v` (the offset data) - returns `OffsetDateTime`
- :zone - translates this per `v` (the zone data) - returns `ZonedDateTime`

In addition, any non-overlapping keys at _lower_ levels (e.g. local-date/time) are also reachable from here.
For example, all the `:julian/*` keys from `LocalDate`, or the individual time fields from `LocalTime` (e.g. `:day/hour`). 

### Instant
Datafies to a (flat) map:

```clj
(d/datafy (Instant/now))
=>
{:second/nano 891207000
 :epoch/second 1581866760
 :epoch/milli 1581866760891
 :epoch/micro 1581866760891207
 :epoch/nano 1581866760891207000}
```
The returned map can be navigated to its top-level keys (returning numbers), 
but also to the following extra keys:

- :format - returns a String per `v` (the formatter pattern - defaults to `ISO_INSTANT`)
- :instant - returns itself as an object (`v` is ignored) 
- :offset - translates this per `v` (the offset data) - returns `OffsetDateTime`
- :zone - translates this per `v` (the zone data) - returns `ZonedDateTime`

## Invalid arguments
How `jedi-time` reacts to keys/values it doesn't recognise (passed mainly to `d/nav`), is controlled by the dynamic Var `jedi-time.core/invalid!`.
By default it throws an `ex-info`, but feel free to re-bind it, or `alter-var-root` it as you see fit  - e.g. `(constantly nil)`.  