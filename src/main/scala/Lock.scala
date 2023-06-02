trait Lock:
  protected inline def thread: Thread =
    Thread.currentThread()
    
  protected inline def threadId: Int =
    thread.getId.toInt

  def lock(): Unit

  def unlock(): Unit

  inline def use[T](inline f: => T): T =
    lock()
    val ret = f
    unlock()
    ret
  end use

end Lock
