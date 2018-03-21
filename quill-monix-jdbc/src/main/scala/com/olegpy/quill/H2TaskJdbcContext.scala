package com.olegpy.quill


import com.olegpy.quill.jdbc.{Runner, TaskJdbcContext, UUIDStringEncoding}

import cats.syntax.applicative._
import java.io.Closeable
import javax.sql.DataSource
import io.getquill.{H2Dialect, NamingStrategy}
import monix.eval.{Task, TaskLocal}

import java.sql.Connection

class H2TaskJdbcContext[N <: NamingStrategy](
  val naming: N,
  dataSource: DataSource with Closeable,
  conn: TaskLocal[Option[Connection]],
  runner: Runner
) extends TaskJdbcContext[H2Dialect, N](dataSource, conn, runner)
  with UUIDStringEncoding
{
  val idiom = H2Dialect
}

object H2TaskJdbcContext extends ContextFactories[H2TaskJdbcContext] {
  override protected def construct[N <: NamingStrategy](naming: N) =
    new H2TaskJdbcContext[N](naming, _, _, _).pure[Task]
}
