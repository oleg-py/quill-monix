package com.olegpy.quill

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
  ): (DataSource with Closeable, TaskLocal[Option[Connection]]) => Task[T[N]]

  def apply[N <: NamingStrategy](naming: N, dataSource: DataSource with Closeable): Task[T[N]] =
    TaskLocal[Option[Connection]](None).flatMap(construct[N](naming)(dataSource, _))

  def apply[N <: NamingStrategy](naming: N, config: JdbcContextConfig): Task[T[N]] =
    apply(naming, config.dataSource)

  def apply[N <: NamingStrategy](naming: N, config: Config): Task[T[N]] =
    apply(naming, JdbcContextConfig(config))

  def apply[N <: NamingStrategy](naming: N, configPrefix: String): Task[T[N]] =
    apply(naming, LoadConfig(configPrefix))
}
