import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.annotation.tailrec
import scala.compiletime.uninitialized

object SimpleLocks:

  final class Spin extends AtomicBoolean(false), Lock:
    @tailrec
    def lock(): Unit =
      if !compareAndSet(false, true)
      then lock()
    def unlock(): Unit = set(false)
  end Spin

  class Node extends AtomicReference[Node](null)

  class FairSpin extends AtomicReference[Node], Lock:
    @volatile private var locked = false
    private var head: Node = uninitialized

    def lock(): Unit =
      val self = Node()

      def acquire(): Unit =
        head = self
        locked = true

      @tailrec
      def spin(): Unit =
        val tail = get()
        val atTail = compareAndSet(tail, self)
        if atTail then
          if tail == null
          then acquire()
          else
            tail.set(self)
            while locked && head.get() != self do ()
            acquire()
          end if
        else spin()
      end spin

      spin()
    end lock

    def unlock(): Unit = locked = false

  end FairSpin


  def main(args: Array[String]): Unit = {

    val lock  = FairSpin()

    def mkThread(i: Int) =
      Thread( () =>
        lock.use {
          println(i)
        }
      )
    end mkThread

    (1 to 10)
      .map(mkThread)
      .tapEach(_.start())
      .foreach(_.join())

  }

end SimpleLocks
