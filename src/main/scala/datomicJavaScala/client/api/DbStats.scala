package datomicJavaScala.client.api


/* todo: other possible stats? */
case class DbStats(
  datoms: Long,
  attrs: Option[Map[String, Int]]
)

