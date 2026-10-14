# Booking Search — Design Reference

How filtered queries work in the booking service: the 3-layer search design,
the patterns behind it, a Criteria-API primer, and exactly what happens at
runtime from HTTP request to SQL. Written as onboarding reference for readers
new to design patterns and the JPA Criteria API.

## 1. Why this design exists

The admin grid once used a single JPQL `@Query` with
`(:param IS NULL OR ...)` guards for optional filters. When a date filter was
omitted, Hibernate bound a `null` JDBC parameter (`$7`) with no inferable type
and PostgreSQL aborted with `SQLState 42P18 (indeterminate datatype)`.

The structural fix: **never bind absent filters at all**. Build the `WHERE`
clause programmatically with the Criteria API, adding a predicate only for
filters the caller actually supplied. The 3 layers below separate *what can be
filtered* (business rules) from *how a filter becomes SQL* (generic engine).

## 2. The three layers

| Layer | Responsibility | Files | Knows about bookings? |
|---|---|---|---|
| 1. Generic engine | Vocabulary (`Operator`, `Filter`) + execution (`GenericSpecification`). Builds and runs queries without knowing any business field. | `repository/search/Operator.java`, `Filter.java`, `GenericSpecification.java` | No |
| 2. Reusable features | Turns any `Filter` into a Criteria `Predicate`: equality, IN, text match, ranges, null checks. | `repository/search/SearchPredicates.java` | No |
| 3. Entity rules | Decides **which filters Booking supports and which attributes they map to**. The only booking-aware layer. | `repository/search/BookingSearch.java`, `repository/BookingMetricsRepository(Impl).java` | Yes |

Dependency rule: **layer 3 → layers 1+2, never the reverse.** Layers 1+2 must
compile with zero imports from the domain package.

```
Controller → Service → BookingSearch (3) → GenericSpecification (1)
                                                   ↓
                                          SearchPredicates (2) → Criteria Predicate → Hibernate → SQL
```

## 3. Design patterns used (and where)

- **Specification pattern** (Evans, Domain-Driven Design): a `Specification<T>`
  is an object representing a query predicate (`toPredicate(...)`) that can be
  combined with `.and()/.or()`. Spring Data's `JpaSpecificationExecutor`
  executes it. `GenericSpecification` is our generic implementation.
- **Factory Method**: `Filter.eq/in/like/gte/...` and
  `BookingSearch.adminGrid/confirmedInRange/...` are named factories — call
  sites read as business language and construction rules (e.g. "empty IN list
  is allowed") live in one place.
- **Strategy (dispatch)**: the `switch` in `SearchPredicates.toPredicate`
  selects the comparison strategy per `Operator`. New operators plug in here.
- **Custom repository fragment**: `BookingMetricsRepository` + the
  naming-convention `...Impl` class extend Spring Data without touching
  framework code — aggregates (`GROUP BY`, `AVG`) that `Specification` cannot
  express, while reusing the same `Filter` lists as the grid.

## 4. Criteria-API primer (the 6 building blocks)

| Block | What it is | Analogy |
|---|---|---|
| `EntityManager em` | JPA entry point; owns query creation and execution. | DB connection + factory |
| `CriteriaBuilder cb` | Builds predicates/expressions: `equal`, `greaterThanOrEqualTo`, `like`, `and`, `count`, `avg`, `conjunction` (SQL `TRUE`). | SQL keyword factory |
| `CriteriaQuery<R>` | The query being assembled; `R` is the result type (`Booking`, `Object[]`, `Double`). | `SELECT ...` statement under construction |
| `Root<Booking> root` | The query's `FROM Booking` handle; `root.get("status")` navigates to an attribute path. | Table alias |
| `Predicate` | One `WHERE` fragment; composite via `cb.and/or`. | `WHERE` condition |
| `Path` / `Expression` | Typed reference to an attribute (`root.get("createdAt")`) usable in comparisons and projections. | Column reference |

Key idea: instead of concatenating JPQL strings, you compose these objects in
Java. Hibernate renders them to parameterized SQL with **correctly typed**
bind variables — which is precisely what the null-guard `@Query` failed to do.

## 5. Runtime flow — how the entity is found

### 5a. List path (`GET /admin/bookings` → grid)

1. **Controller** (`BookingAdminController.getAllBookings`) parses query params
   (`status`, `locationId`, `customerId`, `from`, `to`, `sort`) into Java values
   and a `Pageable`. Nulls pass through untouched.
2. **Service** (`BookingServiceImpl.getAllBookings`) calls
   `BookingSearch.adminGrid(...)`, which returns a `Specification<Booking>`
   wrapping five `Filter`s (field name + operator + value).
3. **Repository call**: `bookingRepository.findAll(spec, pageable)`. The
   repository interface declares the entity once —
   `JpaRepository<Booking, String>` — and Spring Data's `SimpleJpaRepository`
   creates `Root<Booking>` from that domain type and calls
   `spec.toPredicate(root, query, cb)`.
4. **Predicate building**: `GenericSpecification` maps each `Filter` through
   `SearchPredicates`. Absent values yield `cb.conjunction()` (SQL `TRUE`);
   present ones yield typed comparisons (e.g. `createdAt >= ?` with a real
   timestamp bind). The five predicates are AND-ed.
5. **Execution**: Hibernate renders `SELECT ... FROM bookings WHERE ...` with
   only the supplied filters, binds typed parameters, applies
   limit/offset/sort from `Pageable`, and maps rows back to `Booking`.

> "How does it find the entity?" — `GenericSpecification` never names one.
> The entity arrives at step 3 as `Root<T>`, instantiated by Spring Data from
> the *repository's* domain type. `root.get("status")` is then validated
> against Hibernate's runtime metamodel for `Booking`: a misspelled field
> throws `IllegalArgumentException: Unable to locate Attribute` at
> query-build time, never as wrong SQL.

### 5b. Metrics path (`GET /admin/bookings/metrics` → KPIs)

`Specification` only expresses `WHERE` on entity queries, so aggregates use
`EntityManager` directly in `BookingMetricsRepositoryImpl`:

1. Service calls `bookingRepository.countByStatusInRange(from, to)` — the
   method lives on the `BookingMetricsRepository` fragment; Spring Data routes
   it to `BookingMetricsRepositoryImpl` by the `...Impl` naming convention.
2. The impl builds `CriteriaQuery<Object[]>`: `from(Booking.class)` binds the
   entity, `where(...)` reuses **`BookingSearch.createdAtRange(from, to)`** —
   the identical filter list the grid uses — via `SearchPredicates`,
   `multiselect(status, count)` + `groupBy(status)` shapes the projection.
3. The average query is the same shape with `select(cb.avg(grandTotal))` and
   `BookingSearch.confirmedInRange(...)`. JPA defines `AVG` over numbers as
   `Double`; the impl converts to the `BigDecimal` contract (`null` when no
   confirmed row exists), so the service is unchanged.

## 6. How to extend (checklists)

**New filter on an existing search** (e.g. `experienceId` on the grid):
1. Add a constant in `BookingSearch` (entity attribute name, not column name).
2. Add one `Filter.*` line in the relevant factory.
3. Done — engine, predicates, service, and controller plumbing need no changes
   (controller only changes if a new request param is required).

**New endpoint reusing filters** (e.g. vendor dashboard):
1. Add a factory in `BookingSearch` composing existing constants/factories.
2. Service: `findAll(BookingSearch.myNewSearch(...), pageable)` for lists, or a
   new `EntityManager` query with `SearchPredicates.toPredicates(...)` for
   aggregates.

**New comparison type** (e.g. `STARTS_WITH`):
1. Constant in `Operator` → branch in `SearchPredicates` → factory in `Filter`.
2. No entity code changes.

**New entity** (e.g. `PaymentSearch`): create one `XxxSearch` class with its
field constants + factories on top of layers 1+2; repository extends
`JpaSpecificationExecutor<Xxx>`.

## 7. Testing guidance

- **Unit**: `SearchPredicates` and `BookingSearch` factories are pure Java —
  test null/empty handling (expect `conjunction`, i.e. no bind) without a DB.
- **Integration**: `@DataJpaTest` against real PostgreSQL for the grid + both
  metrics methods, especially the all-null-filter case that used to throw
  42P18. H2 is not a substitute here — the bug is PostgreSQL type inference.
