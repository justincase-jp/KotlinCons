package kotlincons

// Basic factory methods

fun <T> consOf(head: T): Cons<T> =
    RandomAccessCons(head, emptyList())

fun <T> consOf(head: T, vararg tail: T): Cons<T> =
    head cons tail

infix fun <T> T.cons(tail: Cons<T>): Cons<T> =
    SequentialCons(this, tail)

infix fun <T> T.cons(tail: List<T>): Cons<T> =
    when (tail) {
      is NestedAccess -> SequentialCons(this, tail)
      is RandomAccess -> RandomAccessCons(this, tail) // Limit the propagation of `RandomAccess` to single level
      else -> SequentialCons(this, tail)
    }

infix fun <T> T.cons(tail: Array<out T>): Cons<T> =
    RandomAccessCons(this, tail.asList())


// Copying factory methods

fun <T> Iterable<T>.toCons(): Cons<T>? =
    when (this) {
      is Cons -> copy<T>()
      is Collection -> when (size) {
        0 -> null
        1 -> consOf((this as? List)?.get(0) ?: iterator().next())
        else -> iterator().unsafeCons
      }
      else -> iterator().toCons()
    }

fun <T> Array<T>.toCons(): Cons<T>? =
    when (size) {
      0 -> null
      1 -> consOf(this[0])
      else -> this[0] cons drop(1)
    }

fun <T> Sequence<T>.toCons(): Cons<T>? =
    iterator().toCons()

private
fun <T> Iterator<T>.toCons(): Cons<T>? =
    if (hasNext()) {
      unsafeCons
    } else {
      null
    }


operator fun <T> Cons<T>.plus(elements: Cons<T>): Cons<T> =
    this + elements.asIterable()

operator fun <T> Cons<T>.plus(elements: Iterable<T>): Cons<T> =
    head cons tail + elements

operator fun <T> Cons<T>.plus(elements: Array<out T>): Cons<T> =
    head cons tail + elements

operator fun <T> Iterable<T>.plus(elements: Cons<T>): Cons<T> =
    when (this) {
      is Cons -> head cons tail + (elements as Iterable<T>)
      is Collection -> when (val n = size) {
        0 -> elements.copy()
        1 -> ((this as? List)?.get(0) ?: iterator().next()) cons elements.toList()
        else -> when (this) {
          is List -> this[0] cons subList(1, n) + elements.asIterable()
          else -> (asSequence() + elements).iterator().unsafeCons
        }
      }
      else -> (asSequence() + elements).iterator().unsafeCons
    }

operator fun <T> Array<T>.plus(elements: Cons<T>): Cons<T> =
    when (val n = size) {
      0 -> elements.copy()
      1 -> this[0] cons elements.toList()
      else -> this[0] cons asList().subList(1, n) + elements.asIterable()
    }


private
val <T> Iterator<T>.unsafeCons: Cons<T>
  get() = next() cons asSequence().toList()

private
fun <T> Cons<T>.copy(): Cons<T> =
    head cons tail.toList()


// Transformation methods

inline fun <T, R> Cons<T>.map(transform: (T) -> R): Cons<R> =
    transform(head) cons tail.map(transform)

inline fun <T, R> Cons<T>.flatMapCons(transform: (T) -> Cons<R>): Cons<R> =
    transform(head).let {
      it.head cons (it.tail.toList() concat tail.flatMap(transform))
    }

fun <T> Cons<Cons<T>>.flatten(): Cons<T> =
    head.head cons (head.tail cons tail).flatten()


// Type utility

fun <T> Cons<T>.asList(): List<T> =
    this


// Type definitions

interface NestedAccess

/**
 * Basically `NonEmptyList`, but implements the [List] interface directly
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
          is RandomAccessCons -> SequentialCons(head, tail, 2, tail.tail)
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
