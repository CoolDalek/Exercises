import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.AtomicIntegerArray

object LExclusion:

  class FilterLock(n: Int) extends Lock:
    private val level = new AtomicIntegerArray(n)
    private val victim = new AtomicIntegerArray(n - 1)

    def lock(): Unit =
      val me = threadId
      var i = 0
      while i < (n - 1) do
        level.set(me, i)
        victim.set(i, me)
        var k = -1
        while
          k += 1
          k < n && level.get(k) >= i && victim.get(i) == me
        do ()
        i += 1
      end while
    end lock

    def unlock(): Unit =
      val me = threadId
      level.set(me, 0)
    end unlock

  end FilterLock

  class LFilterLock(n: Int, l: Int) extends Lock:
    assert(n > l)

    private val level = new AtomicIntegerArray(n)
    private val victim = new AtomicIntegerArray(n - 1)

    def lock(): Unit =
      val me = threadId
      var i = 0
      while i < (n - 1) do
        level.set(me, i)
        victim.set(i, me)
        var k = -1
        while
          k += 1
          k < n && level.get(k) >= i && victim.get(i) == me
        do ()
        i += 1
      end while
    end lock

    def unlock(): Unit = ???

  end LFilterLock

end LExclusion
