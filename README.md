# quill-monix
[Quill](getquill/quill) is great. [Monix](monix/monix) is great.

Let's bring the two closer together.

# Getting started

This library is in a very early stage of development:

* Only JDBC-based H2 support
* No tests at all.

Snapshot version is available, courtesy of [JitPack](https://jitpack.io)

```sbt
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.oleg-py.quill-monix" %% "quill-monix-jdbc" % "cd86cfa728"
```

# Usage
Like you would expect with plain Quill.

```scala
// Required for creating context - only used for query probing and creating a single TaskLocal
import monix.execution.Scheduler.Implicits.global


val ctx = new H2TaskJdbcContext(SnakeCase, "database")

case class User(name: String, age: Int)

// Plain queries return Tasks
val users: Task[List[User]] = ctx.run(ctx.query[User].take(5))

import cats.syntax.functor._ // useful for discarding results

val deleteJon = ctx.run(ctx.query[User].filter(_.name == "Jon").delete.void
val delYounglings = ctx.run(ctx.query[User].filter(_.age <= 15).delete.void

// Since Tasks are lazy, transactions can be added on top of existing ones

val deleteJonAndGetRest = ctx.transaction {
  for {
    _ <- deleteJon
    rest <- users
  } yield rest
}

// Task-based context rely on `TaskLocal`, so local context propagation
// MUST be enabled

implicit val taskOptions = Task.Options.default
  .enableLocalContextPropagation


deleteJonAndGetRest.runAsyncOpt

```
