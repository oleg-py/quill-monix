package com.olegpy.quill.jdbc

import monix.eval.Task
import monix.execution.Scheduler


trait Runner {
  def apply[A](a: => A): Task[A]
}

object Runner {
  val sync: Runner = new Runner {
    override def apply[A](a: => A): Task[A] = Task.eval(a)
  }

  def using(s: Scheduler): Runner = new Runner {
    def apply[A](fa: => A): Task[A] =
      for { // workaround for Monix#612
        s2 <- Task.deferAction(Task.pure)
        _  <- Task.shift(s)
        r  <- Task.eval(fa)
        _  <- Task.shift(s2)
      } yield r
  }
}
