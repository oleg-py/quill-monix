package com.olegpy.quill

import cats.arrow.FunctionK
import cats.{Eval, ~>}
import monix.eval.Task
import monix.execution.Scheduler


package object jdbc {
  type Runner = Eval ~> Task
  object Runner {
    val plain: Runner = FunctionK.lift(Task.fromEval)

    def using(s: Scheduler): Runner = new Runner {
      // TODO: should be just Task.fromEval(fa).executeOn(s), blocked on monix#612
      override def apply[A](fa: Eval[A]): Task[A] =
        for {
          prev <- Task.deferAction(Task.pure)
          _ <- Task.shift(s)
          r <- Task.fromEval(fa)
          _ <- Task.shift(prev)
        } yield r
    }
  }
}
