package kotlincons

import kotlin.let
import kotlin.let as locally

@PublishedApi
internal
infix fun <T> List<T>.concat(suffix: List<T>): List<T> =
    if (
        when (this) {
          is NestedAccess -> false
          is RandomAccess -> when (suffix) {
            is NestedAccess -> false
            is RandomAccess -> true // Limit the propagation of `RandomAccess` to single level
            else -> false
          }
          else -> false
        }
    ) {
      RandomAccessConcatList(this, suffix)
    } else {
      SequentialConcatList(this, suffix)
    }


/**
 * A concatenated view of two lists.
 * External synchronization is required for concurrent modification.
 */
internal
abstract class ConcatList<E> internal constructor(
    val prefix: List<E>,
    val suffix: List<E>
) : AbstractList<E>(), NestedAccess {
  override
  fun isEmpty(): Boolean =
      prefix.isEmpty() && suffix.isEmpty()

  override
  val size: Int
    get() = prefix.size + suffix.size

  override
  fun get(index: Int): E =
      (index - prefix.size).let {
        if (it < 0) {
          prefix[index]
        } else {
          suffix[it]
        }
      }
}

private
class RandomAccessConcatList<E>(prefix: List<E>, suffix: List<E>) : ConcatList<E>(prefix, suffix), RandomAccess

internal
class SequentialConcatList<E>(prefix: List<E>, suffix: List<E>) : ConcatList<E>(prefix, suffix) {
  override
  fun iterator(): Iterator<E> =
      iterator {
        yieldAll(prefix)
        yieldAll(suffix)
      }

  override
  fun listIterator() =
      listIterator(0)

  override
  fun listIterator(index: Int): ListIterator<E> =
      (index - prefix.size).let {
        if (it < 0) {
          createListIterator(true, prefix.listIterator(index))
        } else {
          createListIterator(false, suffix.listIterator(it))
        }
      }

  private
  fun createListIterator(initialIsPrefix: Boolean, initialDelegate: ListIterator<E>): ListIterator<E> =
      object : ListIterator<E> {
        var isPrefix = initialIsPrefix
        var delegate = initialDelegate

        override
        fun hasNext(): Boolean =
            if (isPrefix) suffix.isNotEmpty() else delegate.hasNext()

        override
        fun hasPrevious(): Boolean =
            if (isPrefix) delegate.hasPrevious() else prefix.isNotEmpty()

        override
        fun next(): E =
            locally {
              if (isPrefix && !delegate.hasNext()) {
                isPrefix = false
                delegate = suffix.listIterator()
              }
              delegate.next()
            }

        override
        fun nextIndex(): Int =
            delegate.nextIndex() + if (isPrefix) 0 else prefix.size

        override
        fun previous(): E =
            locally {
              if (!isPrefix && !delegate.hasPrevious()) {
                isPrefix = true
                delegate = prefix.run { listIterator(size) }
              }
              delegate.previous()
            }

        override
        fun previousIndex(): Int =
            delegate.previousIndex() + if (isPrefix) 0 else prefix.size
      }
}
