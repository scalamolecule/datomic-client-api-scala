package datomicScala

import java.util.{Date, List => jList}
import clojure.lang.PersistentVector
import datomic.Util.list
import datomicClient.anomaly.AnomalyWrapper
import datomicScala.client.api.sync._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.specification.core.{Fragments, Text}
import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._


trait Spec extends Specification with SchemaAndData with AnomalyWrapper {
  sequential

  var system    : String     = "Not set yet. Can be: dev-local / peer-server / cloud"
  var client    : Client     = null // set in Setup class
  var conn      : Connection = null // set in Setup class
  var filmDataTx: TxReport   = null // set in Setup class

  var setupException = Option.empty[Throwable]

  override def map(fs: => Fragments): Fragments =
    step(setupDevLocal()) ^
        fs.mapDescription(d => Text(s"$system: " + d.show)) ^
        step(setupPeerServer()) ^
        fs.mapDescription(d => Text(s"$system: " + d.show))

  def setupDevLocal(): Unit = {
    system = "dev-local"
    try {
      client = Datomic.clientDevLocal("Hello system name")
    } catch {
      case t: Throwable =>
        // Catch error from setup (suppressed during setup)
        setupException = Some(t)
    }
  }

  def setupPeerServer(): Unit = {
    system = "peer-server"
    try {
      // With transactor, run two processes in separate tabs:
      // process 1:
      // bin/transactor config/samples/dev-transactor-template.properties
      // process 2:
      // bin/run -m datomic.peer-serverh localhost -p 8998 -a myaccesskey,mysecret -d hello,datomic:dev://localhost:4334/hello

      // In-mem, run Peer Server:
      // bin/run -m datomic.peer-server -a myaccesskey,mysecret -d hello,datomic:mem://hello

      client = Datomic.clientPeerServer("myaccesskey", "mysecret", "localhost:8998")
    } catch {
      case t: Throwable =>
        // Catch error from setup (suppressed during setup)
        setupException = Some(t)
    }
  }


  class Setup extends SchemaAndData with Scope {
    // Throw potential setup error
    setupException.fold()(throw _)

    var txBefore    : Long = 0L
    var txInstBefore: Date = null

    val isDevLocal: Boolean = system == "dev-local"

    if (isDevLocal) {
      // Re-create db
      client.deleteDatabase("hello")
      client.deleteDatabase("world")
      client.createDatabase("hello")
      conn = client.connect("hello")
      // Schema
      val schemaTx = conn.transact(schema(false))
      txBefore = schemaTx.tx
      txInstBefore = schemaTx.txInst
      // Data
      filmDataTx = conn.transact(filmData)

    } else {
      var lastTxOpt = Option.empty[TxReport]

      conn = client.connect("hello")
      // Install schema if necessary
      if (Datomic.q(
        "[:find ?e :where [?e :db/ident :movie/title]]",
        conn.db
      ).toString == "[]") {
        println("Installing Peer Server hello db schema...")
        lastTxOpt = Some(conn.transact(schema(true)))
      }

      // Retract current data
      Datomic.q("[:find ?e :where [?e :movie/title _]]", conn.db)
        .asInstanceOf[jList[_]].forEach { l =>
        val eid: Any = l.asInstanceOf[jList[_]].get(0)
        lastTxOpt = Some(conn.transact(list(list(":db/retractEntity", eid))))
      }

      lastTxOpt.fold {
        val txDatom = conn.txRange(None, None).last._2.head
        txBefore = txDatom.tx
        txInstBefore = txDatom.v.asInstanceOf[Date]
      } { lastTx =>
        txBefore = lastTx.tx
        txInstBefore = lastTx.txInst
      }

      filmDataTx = conn.transact(filmData)
    }

    lazy val dbAfter          = filmDataTx.dbAfter
    lazy val tBefore          = filmDataTx.basisT
    lazy val tAfter           = filmDataTx.t
    lazy val txAfter          = filmDataTx.tx
    lazy val txInstAfter      = filmDataTx.txInst
    lazy val List(e1, e2, e3) = filmDataTx.txData.toScala(List).map(_.e).distinct.drop(1).sorted

    // Ids of the three attributes
    val List(a1, a2, a3) = if (system == "dev-local")
      List(73, 74, 75) else List(72, 73, 74)
    //      List(73, 74, 75) else List(63, 64, 65) // sometimes this is needed instead...

    def films(db: Db): Seq[String] = Datomic.q(filmQuery, db)
      .asInstanceOf[PersistentVector].asScala.toList
      .map(row => row.asInstanceOf[PersistentVector].asScala.head.toString)
      .sorted
  }
}