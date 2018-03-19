package com.olegpy.quill.jdbc

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.util.Try

import cats.data.OptionT
import io.getquill.NamingStrategy
import io.getquill.context.Context
import io.getquill.context.sql.SqlContext
import io.getquill.context.sql.idiom.SqlIdiom
import io.getquill.util.ContextLogger
import monix.eval.{Task, TaskLocal}
import monix.execution.Scheduler

import java.io.Closeable
import java.sql.{Array => _, _}
import javax.sql.DataSource


/**
  * Quill context that wraps all JDBC calls in `monix.eval.Task`.
  *
  * @param scheduler - Monix scheduler - used ONLY for query probing
  *                  and unsafely creating a TaskLocal with current connection
  */
abstract class TaskJdbcContext[Dialect <: SqlIdiom, Naming <: NamingStrategy](
  dataSource: DataSource with Closeable
)(
  implicit scheduler: Scheduler
)
  extends Context[Dialect, Naming]
    with  SqlContext[Dialect, Naming]
    with  Encoders
    with  Decoders
{
  /**
    * This function is used for wrapping JDBC calls into a `Task`
    *
    * Can be used to provide custom scheduler (using `shift`/`executeOn`),
    * forcing async boundaries and/or using `blocking`
    *
    * By default this uses plain simple `Task.eval`
    */
  protected def delay[A](thunk: => A): Task[A] = Task.eval(thunk)

  private[this] val currentConnection: TaskLocal[Option[Connection]] =
    TaskLocal(None: Option[Connection]).runSyncUnsafe(Duration.Inf)

  private[this] val logger = ContextLogger(classOf[TaskJdbcContext[_, _]])

  override type PrepareRow = PreparedStatement
  override type ResultRow = ResultSet

  override type Result[T] = Task[T]
  override type RunQueryResult[T] = List[T]
  override type RunQuerySingleResult[T] = T
  override type RunActionResult = Long
  override type RunActionReturningResult[T] = T
  override type RunBatchActionResult = List[Long]
  override type RunBatchActionReturningResult[T] = List[T]

  private[this] val closeConn = (c: Connection) => Task.eval(c.close())

  protected def withConnection[A](f: Connection => Task[A]): Task[A] =
    OptionT(currentConnection.read).semiflatMap(f).getOrElseF {
      for {
        conn <- Task.eval(dataSource.getConnection)
        res  <- currentConnection.bind(Some(conn)) {
          Task.pure(conn).bracket(f)(closeConn)
        }
      } yield res
    }

  private[this] def withConnectionDelay[A](f: Connection => A): Task[A] =
    withConnection(conn => delay(f(conn)))

  override def close(): Unit = dataSource.close()

  override def probe(sql: String): Try[_] =
    withConnection(c => Task.eval(c.createStatement.execute(sql)))
      .materialize
      .runSyncUnsafe(Duration.Inf)

  def transaction[A](f: Task[A]): Task[A] =
    OptionT(currentConnection.read).semiflatMap(_ => f).getOrElseF {
      withConnection { conn =>
        for {
          wasAutoCommit <- Task.eval(conn.getAutoCommit)
          result <- Task.eval(conn.setAutoCommit(false))
            .bracketE(_ => f) {
              case (_, Right(_)) => Task.eval(conn.commit())
              case (_, Left(_)) => Task.eval(conn.rollback())
            }
            .doOnFinish(_ => Task.eval(conn.setAutoCommit(wasAutoCommit)))
        } yield result
      }
    }


  def executeQuery[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Task[List[T]] =
    withConnectionDelay { conn =>
      val (params, ps) = prepare(conn.prepareStatement(sql))
      logger.logQuery(sql, params)
      val rs = ps.executeQuery()
      extractResult(rs, extractor)
    }

  def executeQuerySingle[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Task[T] =
    executeQuery(sql, prepare, extractor).map(handleSingleResult)

  def executeAction[T](sql: String, prepare: Prepare = identityPrepare): Task[Long] =
    withConnectionDelay { conn =>
      val (params, ps) = prepare(conn.prepareStatement(sql))
      logger.logQuery(sql, params)
      ps.executeUpdate().toLong
    }

  def executeActionReturning[O](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[O], returningColumn: String): Task[O] =
    withConnectionDelay { conn =>
      val (params, ps) = prepare(conn.prepareStatement(sql, Array(returningColumn)))
      logger.logQuery(sql, params)
      ps.executeUpdate()
      handleSingleResult(extractResult(ps.getGeneratedKeys, extractor))
    }

  def executeBatchAction(groups: List[BatchGroup]): Task[List[Long]] =
    withConnectionDelay { conn =>
      groups.flatMap {
        case BatchGroup(sql, prepare) =>
          val ps = conn.prepareStatement(sql)
          logger.underlying.debug("Batch: {}", sql)
          prepare.foreach { f =>
            val (params, _) = f(ps)
            logger.logBatchItem(sql, params)
            ps.addBatch()
          }
          ps.executeBatch().map(_.toLong)
      }
    }

  def executeBatchActionReturning[T](groups: List[BatchGroupReturning], extractor: Extractor[T]): Task[List[T]] =
    withConnectionDelay { conn =>
      groups.flatMap {
        case BatchGroupReturning(sql, column, prepare) =>
          val ps = conn.prepareStatement(sql, Array(column))
          logger.underlying.debug("Batch: {}", sql)
          prepare.foreach { f =>
            val (params, _) = f(ps)
            logger.logBatchItem(sql, params)
            ps.addBatch()
          }
          ps.executeBatch()
          extractResult(ps.getGeneratedKeys, extractor)
      }
    }

  /**
    * Parses instances of java.sql.Types to string form so it can be used in creation of sql arrays.
    * Some databases does not support each of generic types, hence it's welcome to override this method
    * and provide alternatives to non-existent types.
    *
    * @param intType one of java.sql.Types
    * @return JDBC type in string form
    */
  def parseJdbcType(intType: Int): String = JDBCType.valueOf(intType).getName

  private def extractResult[T](rs: ResultSet, extractor: Extractor[T], acc: List[T] = List()): List[T] = {
    val b = ListBuffer.empty[T]
    while (rs.next()) {
      b += extractor(rs)
    }
    b.result()
  }
}
