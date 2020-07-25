package kotlincons

// Basic factory methods

fun <T> consOf(head: T): Cons<T> =
    head cons emptyList()

fun <T> consOf(head: T, vararg tail: T): Cons<T> =
    head cons tail

infix fun <T> T.cons(tail: List<T>): Cons<T> =
    when (tail) {
      is NestedAccess -> SequentialCons(this, tail)
      is RandomAccess -> RandomAccessCons(this, tail) // Limit the propagation of `RandomAccess` to single level
      else -> SequentialCons(this, tail)
    }

infix fun <T> T.cons(tail: Array<out T>): Cons<T> =
    this cons tail.asList()


// Copying factory methods

operator fun <T> Cons<T>.plus(elements: Iterable<T>): Cons<T> =
    head cons tail + elements

operator fun <T> Cons<T>.plus(elements: Array<out T>): Cons<T> =
    head cons tail + elements

operator fun <T> Iterable<T>.plus(elements: Cons<T>): Cons<T> =
    toCons()
        ?.let { it.head cons ConcatList.of(it.tail, elements.copy()) }
        ?: elements.copy()

operator fun <T> Array<T>.plus(elements: Cons<T>): Cons<T> =
    toCons()
        ?.let { it.head cons ConcatList.of(it.tail, elements.copy()) }
        ?: elements.copy()


fun <T> Array<T>.toCons(): Cons<T>? =
    when (size) {
      0 -> null
      1 -> consOf(this[0])
      else -> this[0] cons drop(1)
    }

fun <T> Iterable<T>.toCons(): Cons<T>? =
    when (this) {
      is Cons -> copy<T>()
      is Collection -> when (size) {
        0 -> null
        1 -> consOf((this as? List)?.get(0) ?: iterator().next())
        else -> iterator().toCons()
      }
      else -> iterator().let {
        if (it.hasNext()) {
          it.toCons()
        } else {
          null
        }
      }
    }

private
fun <T> Iterator<T>.toCons(): Cons<T>? =
    next() cons asSequence().toList()

private
fun <T> Cons<T>.copy(): Cons<T> =
    head cons tail.toList()


// Type definitions

interface NestedAccess

/**
 * Basically `NonEmptyList`, but extends the [List] interface directly
 */
abstract class Cons<out E> internal constructor(val head: E, val tail: List<E>) : AbstractList<E>(), NestedAccess {
  override
  fun isEmpty(): Boolean =
      false

  override
  val size: Int
    get() = 1 + tail.size

  override
  fun get(index: Int): E =
      when (index) {
        0 -> head
        else -> tail[index - 1]
      }


  fun <R> map(transform: (E) -> R): Cons<R> =
      transform(head) cons tail.map(transform)

  fun <R> flatMapCons(transform: (E) -> Cons<R>): Cons<R> =
      transform(head).let {
        it.head cons ConcatList.of(it.tail, tail.flatMap(transform))
      }
}

private
class RandomAccessCons<out E>(head: E, tail: List<E>) : Cons<E>(head, tail), RandomAccess

private
class SequentialCons<out E>(
    head: E,
    tail: List<E>,
    val prefixSize: Int,
    val suffix: List<E>
) : Cons<E>(head, tail) {
  companion object {
    operator fun <E> invoke(head: E, tail: List<E>): SequentialCons<E> =
        when (tail) {
          is RandomAccessCons -> SequentialCons(head, tail, 1, tail.tail)
          is SequentialCons -> SequentialCons(head, tail, 1 + tail.prefixSize, tail.suffix)
          else -> SequentialCons(head, tail, 1, tail)
        }
  }

  override
  val size: Int
    get() = prefixSize + suffix.size

  override
  fun iterator(): Iterator<E> =
      iterator {
        yield(head)
        yieldAll(tail)
      }

  override
  fun listIterator(): ListIterator<E> =
      listIterator(0)

  override
  fun listIterator(index: Int): ListIterator<E> =
      SequentialConcatList(LazyList(asSequence().take(prefixSize).iterator()), suffix).listIterator(index)
}
