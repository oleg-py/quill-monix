package com.olegpy.quill


import com.olegpy.quill.jdbc.{TaskJdbcContext, UUIDStringEncoding}

import java.io.Closeable
import javax.sql.DataSource
import com.typesafe.config.Config
import io.getquill.{H2Dialect, JdbcContextConfig, NamingStrategy}
import io.getquill.util.LoadConfig
import monix.execution.Scheduler

class H2TaskJdbcContext[N <: NamingStrategy](
  val naming: N,
  dataSource: DataSource with Closeable
)(implicit s: Scheduler)
  extends TaskJdbcContext[H2Dialect, N](dataSource) with UUIDStringEncoding {

  def this(naming: N, config: JdbcContextConfig)(implicit s: Scheduler) =
    this(naming, config.dataSource)

  def this(naming: N, config: Config)(implicit s: Scheduler) =
    this(naming, JdbcContextConfig(config))

  def this(naming: N, configPrefix: String)(implicit s: Scheduler) =
    this(naming, LoadConfig(configPrefix))

  val idiom = H2Dialect
}
