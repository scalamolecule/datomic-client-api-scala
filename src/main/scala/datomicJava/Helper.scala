package datomicJava

import java.lang.{Iterable => jIterable}
import java.util
import java.util.stream.{StreamSupport, Stream => jStream}
import java.util.{Spliterator, Spliterators, Iterator => jIterator, Map => jMap}
import clojure.lang._
import datomic.Util.read
import datomic.core.db.Datum
import datomicJava.client.api.{Datom, DbStats}
import javafx.util.Pair
import scala.jdk.CollectionConverters._

object Helper {

  def getDatom(d: Datum): Datom = Datom(
    d.e, d.a, d.v, d.tx.asInstanceOf[Long], d.added()
  )

  def getDatom(d: ILookup): Datom = Datom(
    d.valAt(read(":e")).asInstanceOf[Long],
    d.valAt(read(":a")),
    d.valAt(read(":v")),
    d.valAt(read(":tx")).asInstanceOf[Long],
    d.valAt(read(":added")).asInstanceOf[Boolean]
  )

  // Unify Datoms in single fast iteration
  def streamOfDatoms(rawDatoms: Any): jStream[Datom] = {
    rawDatoms match {
      // Dev-local only
      // Getting Datom values without reflection
      case lazySeq: LazySeq => mkStream(
        new jIterator[Datom] {
          val it: jIterator[_] = lazySeq.iterator
          override def hasNext: Boolean = it.hasNext
          override def next(): Datom = getDatom(it.next.asInstanceOf[Datum])
        }
      )

      // Peer Server + Dev-local
      // Gets Datom values with reflection through ILookup interface
      case iterable: java.lang.Iterable[_] => mkStream(
        new jIterator[Datom] {
          val it: jIterator[_] = iterable.iterator
          override def hasNext: Boolean = it.hasNext
          override def next(): Datom = getDatom(it.next.asInstanceOf[ILookup])
        }
      )
    }
  }

  private def mkStream(it: jIterator[Datom]): jStream[Datom] = StreamSupport.stream(
    Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED),
    false
  )

  def nestedTxsIterable(
    isDevLocal: Boolean,
    rawTxs0: AnyRef
  ): jIterable[Pair[Long, jIterable[Datom]]] = {
    if (isDevLocal) {
      val rawTxs = rawTxs0.asInstanceOf[clojure.lang.LazySeq]
      // Iterable with txs
      new jIterable[Pair[Long, jIterable[Datom]]] {
        override def iterator: jIterator[Pair[Long, jIterable[Datom]]] = {
          new jIterator[Pair[Long, jIterable[Datom]]] {
            val it: jIterator[_] = rawTxs.iterator
            override def hasNext: Boolean = it.hasNext
            override def next(): Pair[Long, jIterable[Datom]] = {
              val tx          = it.next().asInstanceOf[PersistentArrayMap]
              val t           = tx.get(read(":t")).asInstanceOf[Long]
              val rawTxDatoms = tx.get(read(":data")).asInstanceOf[PersistentVector]

              // Iterable with Datoms of this tx
              val txDatoms = new jIterable[Datom] {
                override def iterator: jIterator[Datom] = {
                  new jIterator[Datom] {
                    private val it = rawTxDatoms.iterator()
                    override def hasNext: Boolean = it.hasNext
                    override def next(): Datom = {
                      getDatom(it.next.asInstanceOf[Datum])
                    }
                  }
                }
              }
              new Pair(t, txDatoms)
            }
          }
        }
      }
    } else {
      val rawTxs = rawTxs0.asInstanceOf[java.lang.Iterable[_]]
      // Iterable with txs
      new jIterable[Pair[Long, jIterable[Datom]]] {
        override def iterator: jIterator[Pair[Long, jIterable[Datom]]] = {
          new jIterator[Pair[Long, jIterable[Datom]]] {
            val it: jIterator[_] = rawTxs.iterator
            override def hasNext: Boolean = it.hasNext
            override def next(): Pair[Long, jIterable[Datom]] = {
              val tx          = it.next().asInstanceOf[PersistentArrayMap]
              val t           = tx.get(read(":t")).asInstanceOf[Long]
              val rawTxDatoms = tx.get(read(":data")).asInstanceOf[PersistentVector]

              // Iterable with Datoms of this tx
              val txDatoms = new jIterable[Datom] {
                override def iterator: jIterator[Datom] = {
                  new jIterator[Datom] {
                    private val it2 = rawTxDatoms.iterator()
                    override def hasNext: Boolean = it2.hasNext
                    override def next(): Datom = {
                      getDatom(it2.next.asInstanceOf[ILookup])
                    }
                  }
                }
              }
              new Pair(t, txDatoms)
            }
          }
        }
      }
    }
  }

  def nestedTxsArray(
    isDevLocal: Boolean,
    rawTxs0: AnyRef
  ): Array[Pair[Long, Array[Datom]]] = {
    // Create multi-dimensional Array
    if (isDevLocal) {
      val raw = rawTxs0.asInstanceOf[clojure.lang.LazySeq]
      val txs = new Array[Pair[Long, Array[Datom]]](raw.size())
      var i   = 0
      raw.forEach { tx0 =>
        val tx          = tx0.asInstanceOf[PersistentArrayMap]
        val t           = tx.get(read(":t")).asInstanceOf[Long]
        val rawTxDatoms = tx.get(read(":data")).asInstanceOf[PersistentVector]
        val txDatoms    = new Array[Datom](rawTxDatoms.size())
        var j           = 0
        rawTxDatoms.forEach { d0 =>
          txDatoms(j) = getDatom(d0.asInstanceOf[Datum])
          j += 1
        }
        txs(i) = new Pair(t, txDatoms)
        i += 1
      }
      txs

    } else {

      val raw = rawTxs0.asInstanceOf[java.lang.Iterable[_]]
      val txs = new util.ArrayList[Pair[Long, Array[Datom]]]()
      var i   = 0
      raw.forEach { tx0 =>
        val tx          = tx0.asInstanceOf[PersistentArrayMap]
        val t           = tx.get(read(":t")).asInstanceOf[Long]
        val rawTxDatoms = tx.get(read(":data")).asInstanceOf[PersistentVector]
        val txDatoms    = new Array[Datom](rawTxDatoms.size())
        var j           = 0
        rawTxDatoms.forEach { d0 =>
          txDatoms(j) = getDatom(d0.asInstanceOf[ILookup])
          j += 1
        }
        txs.add(new Pair(t, txDatoms))
        i += 1
      }
      txs.toArray(new Array[Pair[Long, Array[Datom]]](i))
    }
  }


  def dbStats(isDevLocal: Boolean, raw: jMap[_, _]): DbStats = {
    val datoms   = raw.get(read(":datoms")).asInstanceOf[Long]
    val attrsRaw = raw.get(read(":attrs"))
    if (attrsRaw != null) {
      val count = if (isDevLocal)
        (rawCount: Any) => rawCount.asInstanceOf[Int].toLong
      else
        (rawCount: Any) => rawCount.asInstanceOf[Long]
      val jmap  = new util.HashMap[String, Long]()
      attrsRaw.asInstanceOf[jMap[_, _]].asScala.toMap.foreach {
        case (kw: Keyword, m: jMap[_, _]) =>
          jmap.put(kw.toString, count(m.asScala.head._2))

        case otherPair => throw new RuntimeException(
          "Unexpected pair from db-stats: " + otherPair
        )
      }
      DbStats(datoms, jmap)
    } else {
      DbStats(datoms, null)
    }
  }
}