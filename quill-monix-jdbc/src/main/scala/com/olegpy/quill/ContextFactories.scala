package com.olegpy.quill

import com.olegpy.quill.jdbc.Runner
import com.typesafe.config.Config
import io.getquill.NamingStrategy
import io.getquill.util.LoadConfig
import monix.eval.{Task, TaskLocal}

import java.io.Closeable
import java.sql.Connection
import javax.sql.DataSource


trait ContextFactories[T[_ <: NamingStrategy]] {
  protected def construct[N <: NamingStrategy](
    naming: N
  ): (DataSource with Closeable, TaskLocal[Option[Connection]], Runner) => Task[T[N]]

  def apply[N <: NamingStrategy](naming: N, dataSource: DataSource with Closeable, runner: Runner): Task[T[N]] =
    TaskLocal[Option[Connection]](None).flatMap(construct[N](naming)(dataSource, _, runner))

  def apply[N <: NamingStrategy](naming: N, config: JdbcContextConfig, runner: Runner): Task[T[N]] =
    apply(naming, config.dataSource, runner)

  def apply[N <: NamingStrategy](naming: N, config: Config, runner: Runner): Task[T[N]] =
    apply(naming, JdbcContextConfig(config), runner)

  def apply[N <: NamingStrategy](naming: N, configPrefix: String, runner: Runner): Task[T[N]] =
    apply(naming, LoadConfig(configPrefix), runner)
}
