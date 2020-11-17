package datomicJava.client.api.async

import java.util.concurrent.CompletableFuture
import java.util.stream.{Stream => jStream}
import java.util.{Date, List => jList, Map => jMap}
import clojure.lang.ASeq
import datomic.Util._
import datomicClojure.{ErrorMsg, Invoke, InvokeAsync, Lookup}
import datomicJava.client.api.{Datom, DbStats, async}
import datomicJava.{CognitectAnomaly, Helper}


case class AsyncDb(datomicDb: AnyRef) extends Lookup(datomicDb) {


  def dbStats: CompletableFuture[Either[CognitectAnomaly, DbStats]] = {
    CompletableFuture.supplyAsync { () =>
      Channel[jMap[_, _]](
        InvokeAsync.dbStats(datomicDb)
      ).chunk
    }.thenApply {
      case Right(dbStats: jMap[_, _]) =>
        Channel[DbStats](Helper.dbStats(isDevLocal, dbStats)).chunk
      case Left(anomaly)              => async.Left(anomaly)
    }
  }

  // Time filters --------------------------------------

  def asOf(t: Long): AsyncDb = AsyncDb(InvokeAsync.asOf(datomicDb, t))
  def asOf(d: Date): AsyncDb = AsyncDb(InvokeAsync.asOf(datomicDb, d))

  def since(t: Long): AsyncDb = AsyncDb(InvokeAsync.since(datomicDb, t))
  def since(d: Date): AsyncDb = AsyncDb(InvokeAsync.since(datomicDb, d))

  def `with`(withDb: AnyRef, stmts: jList[_])
  : CompletableFuture[Either[CognitectAnomaly, AsyncDb]] = {
    if (withDb.isInstanceOf[AsyncDb])
      throw new IllegalArgumentException(ErrorMsg.`with`)
    CompletableFuture.supplyAsync { () =>
      Channel[AnyRef](
        InvokeAsync.`with`(withDb, stmts)
      ).chunk
    }.thenApply {
      case Right(withDb) =>
        Channel[AsyncDb](
          AsyncDb(
            withDb.asInstanceOf[jMap[_, _]]
              .get(read(":db-after")).asInstanceOf[AnyRef]
          )
        ).chunk
      case Left(anomaly) => async.Left(anomaly)
    }
  }

  def history: AsyncDb = AsyncDb(
    InvokeAsync.history(datomicDb)
  )


  // Indexes --------------------------------------

  /**
   *
   * @param index      String :eavt, :aevt, :avet, or :vaet
   * @param components Optional vector in the same order as the index
   *                   containing one or more values to further narrow the
   *                   result.
   * @return List[datomicFacade.client.api.Datom] Wrapped Datoms with a unified api
   */
  def datoms(index: String, components: jList[_])
  : CompletableFuture[Either[CognitectAnomaly, jStream[Datom]]] = {
    CompletableFuture.supplyAsync { () =>
      Channel[Any](
        InvokeAsync.datoms(datomicDb, index, components)
      ).chunk
    }.thenApply {
      case Right(datoms) => Channel[jStream[Datom]](
        Helper.streamOfDatoms(datoms)
      ).chunk
      case Left(anomaly) => async.Left(anomaly)
    }
  }

  def indexRange(
    attrId: String,
    start: Any,
    end: Any,
    timeout: Int,
    offset: Int,
    limit: Int
  ): CompletableFuture[Either[CognitectAnomaly, jStream[Datom]]] = {
    CompletableFuture.supplyAsync { () =>
      Channel[Any](
        InvokeAsync.indexRange(
          datomicDb, attrId, Option(start), Option(end), timeout, offset, limit
        )
      ).chunk
    }.thenApply {
      case Right(datoms) => Channel[jStream[Datom]](
        Helper.streamOfDatoms(datoms)
      ).chunk
      case Left(anomaly) => async.Left(anomaly)
    }
  }

  def indexRange[T](attrId: String)
  : CompletableFuture[Either[CognitectAnomaly, jStream[Datom]]] =
    indexRange(attrId, null, null, 0, 0, 1000)

  def indexRange[T](attrId: String, start: Long)
  : CompletableFuture[Either[CognitectAnomaly, jStream[Datom]]] =
    indexRange(attrId, start, null, 0, 0, 1000)

  def indexRange[T](
    attrId: String,
    start: Any,
    end: Any
  ): CompletableFuture[Either[CognitectAnomaly, jStream[Datom]]] =
    indexRange(attrId, start, end, 0, 0, 1000)


  // Pull --------------------------------------

  def pull(
    selector: String,
    eid: Any,
    timeout: Int,
    offset: Int,
    limit: Int
  ): CompletableFuture[Either[CognitectAnomaly, jMap[_, _]]] = {
    CompletableFuture.supplyAsync { () =>
      Channel[jMap[_, _]](
        InvokeAsync.pull(datomicDb, selector, eid, timeout, offset, limit)
      ).chunk
    }
  }

  def pull(selector: String, eid: Any)
  : CompletableFuture[Either[CognitectAnomaly, jMap[_, _]]] =
    pull(selector, eid, 0, 0, 1000)

  def pull(selector: String, eid: Any, limit: Int)
  : CompletableFuture[Either[CognitectAnomaly, jMap[_, _]]] =
    pull(selector, eid, 0, 0, limit)


  def indexPull(
    index: String,
    selector: String,
    start: String,
    reverse: Boolean,
    timeout: Int,
    offset: Int,
    limit: Int,
  ): CompletableFuture[Either[CognitectAnomaly, jStream[_]]] = {
    if (!Seq(":avet", ":aevt").contains(index))
      throw new IllegalArgumentException(ErrorMsg.indexPull)
    CompletableFuture.supplyAsync { () =>
      Channel[Any](
        Invoke.indexPull(
          datomicDb, index, selector, start, reverse, timeout, offset, limit
        )
      ).chunk
    }.thenApply {
      case Right(indexPull) => Channel[jStream[_]](
        indexPull.asInstanceOf[ASeq].stream()
      ).chunk
      case Left(anomaly)    => async.Left(anomaly)
    }
  }

  def indexPull(
    index: String,
    selector: String,
    start: String
  ): CompletableFuture[Either[CognitectAnomaly, jStream[_]]] = {
    indexPull(index, selector, start, false, 0, 0, 1000)
  }

  def indexPull(
    index: String,
    selector: String,
    start: String,
    reverse: Boolean
  ): CompletableFuture[Either[CognitectAnomaly, jStream[_]]] = {
    indexPull(index, selector, start, reverse, 0, 0, 1000)
  }

  def indexPull(
    index: String,
    selector: String,
    start: String,
    reverse: Boolean,
    limit: Int,
  ): CompletableFuture[Either[CognitectAnomaly, jStream[_]]] = {
    indexPull(index, selector, start, reverse, 0, 0, limit)
  }
}