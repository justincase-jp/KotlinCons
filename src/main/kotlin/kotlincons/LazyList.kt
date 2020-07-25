package kotlincons

/**
 * A list implementation that evaluate and store results of the supplied iterator.
 * External synchronization is required for concurrent access.
 */
class LazyList<out E>(private val iterator: Iterator<E>) : AbstractList<E>(), RandomAccess {
  private
  val delegate = mutableListOf<E>()

  override
  val size: Int
    get() = iterator.run {
      while (hasNext()) {
        delegate += next()
      }
      delegate.size
    }

  override
  tailrec fun get(index: Int): E =
      when {
        index < delegate.size -> delegate[index]
        else -> {
          delegate += iterator.next()
          get(index)
        }
      }
}
