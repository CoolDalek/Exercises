import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.*
import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.mtl.*

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.duration.*

object Philosophers extends IOApp.Simple:

  case class Philosopher(self: Int, leftStick: Int, rightStick: Int)
  given Show[Vector[Philosopher]] = _.map(_.self).mkString("(", ",", ")")

  def philosophers(n: Int): Vector[Philosopher] =
    Vector.tabulate(n) { self =>
      val left =
        val candidate = self - 1
        if candidate < 0
        then n - 1
        else candidate
      end left

      Philosopher(self, left, self)
    }
  end philosophers

  type Stop[F[_]] = Int => F[Boolean]

  def stopCheck[F[_]: Temporal](timeout: FiniteDuration): F[Stop[F]] =
      Clock[F].realTimeInstant
        .map { now =>
          now.plus(timeout.length, timeout.unit.toChronoUnit)
        }
        .memoize
        .map { deadline => (iteration: Int) =>
          for
            now <- Clock[F].realTimeInstant
            max <- deadline
          yield !now.isBefore(max)
        }
  end stopCheck

  type Metrics = Map[Int, Int]
  type Collect[F[_]] = Vector[Philosopher] => F[Unit]

  def collectMetrics[F[_]](using stateful: Stateful[F, Metrics]): Collect[F] =
    active => stateful.modify { metrics =>
      active.foldLeft(metrics) { (m, p) =>
        val current = m.getOrElse(p.self, 0)
        m.updated(p.self, current + 1)
      }
    }
  end collectMetrics

  type Scheduler = Vector[Philosopher] => (Vector[Philosopher], Vector[Philosopher])

  def schedule(queue: Vector[Philosopher]): (Vector[Philosopher], Vector[Philosopher]) =
    val eating = Vector.newBuilder[Philosopher]
    val blocked = Vector.newBuilder[Philosopher]
    val claimed = mutable.Set.empty[Int]

    queue.foreach { p =>
      val isBlocked = claimed.contains(p.leftStick) || claimed.contains(p.rightStick)
      if isBlocked then
        blocked.addOne(p)
      else
        eating.addOne(p)
        claimed.addOne(p.leftStick)
        claimed.addOne(p.rightStick)
      end if
    }

    val active = eating.result()
    val updated = blocked.addAll(active).result()
    (active, updated)
  end schedule

  def dining[F[_]: Monad](
                           philosophers: Vector[Philosopher],
                           schedule: Scheduler,
                           metrics: Collect[F],
                           checkStop: Stop[F],
                         ): F[Unit] =

    def loop(philosophers: Vector[Philosopher], iteration: Int): F[Unit] =
      val (active, queue) = schedule(philosophers)
      metrics(active) *> {
        checkStop(iteration).flatMap { stop =>
          if stop
          then Applicative[F].unit
          else loop(queue, iteration + 1)
        }
      }
    end loop

    loop(philosophers, 0)
  end dining

  type Context[x] = StateT[IO, Metrics, x]

  val run: IO[Unit] =

    val stopCheckCtx =
      def lift[T](io: IO[T]): Context[T] = StateT.liftF(io)

      val ioCtx =
        stopCheck[IO](10 seconds span)
          .map { check =>
            (iteration: Int) => lift(check(iteration))
          }

      lift(ioCtx)
    end stopCheckCtx

    val stateful = for
      stop <- stopCheckCtx
      metrics = collectMetrics[Context]
      queue = philosophers(5)
      _ <- dining[Context](
        queue,
        schedule,
        metrics,
        stop,
      )
    yield ()
    stateful.runS(Map.empty).flatMap(IO.println)
  end run

end Philosophers
