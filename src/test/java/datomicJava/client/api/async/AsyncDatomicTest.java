package datomicJava.client.api.async;

import datomicJava.Forbidden;
import datomicJava.NotFound;
import datomicJava.SetupAsync;
import datomicJava.client.api.async.AsyncClient;
import datomicJava.client.api.async.AsyncDatomic;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.stream.Stream;

import static datomic.Util.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;


@FixMethodOrder(MethodSorters.JVM)
public class AsyncDatomicTest extends SetupAsync {

    public AsyncDatomicTest(String name) {
        system = name;
    }

    @Test
    public void createClient() {
        if (system == "dev-local") {
          /*
            Install dev-local (https://docs.datomic.com/cloud/dev-local.html)
            > mkdir ~/.datomic
            > touch ~/.datomic/dev-local.edn
            > open ~/.datomic/dev-local.edn
            add path to where you want to save data as per instructions in link above

            Add dependency to dev-local in your project
            "com.datomic" % "dev-local" % "0.9.195",

            As long dev-local has a dependency on clojure 1.10.0-alpha4
            we also need to import a newer version of clojure
            "org.clojure" % "clojure" % "1.10.1",

            (No need to start a transactor)
           */

            // Retrieve client for a specific system
            // (this one has been created in SetupSpec)
            AsyncClient client = AsyncDatomic.clientDevLocal("Hello system name");

            // Confirm that client is valid and can connect to a database
            client.connect("hello");

            // Wrong system name
            // todo - Shouldn't this throw a failure exception?
            NotFound wrongSystemName = assertThrows(
                NotFound.class,
                () -> AsyncDatomic.clientDevLocal("x").connect("hello")
            );
            assertThat(wrongSystemName.msg(), is("Db not found: hello"));

            // Wrong db name
            // todo - Shouldn't this throw a failure exception?
            NotFound wrongDbName = assertThrows(
                NotFound.class,
                () -> AsyncDatomic.clientDevLocal("Hello system name").connect("7")
            );
            assertThat(wrongDbName.msg(), is("Db not found: y"));


        } else if (system == "peer-server") {
            /*
              To run tests against a Peer Server do these 3 steps first:

              1. Start transactor
              > bin/transactor config/samples/free-transactor-template.properties

              2. Create sample db 'hello' by running 'create hello db' test (only) in CreateTestDb
              Peer.createDatabase("datomic:free://localhost:4334/hello")

              Start Peer Server for some existing database (like `hello` here)
              > bin/run -m datomic.peer-server -h localhost -p 8998 -a myaccesskey,mysecret -d hello,datomic:dev://localhost:4334/hello
             */

            AsyncClient client = AsyncDatomic.clientPeerServer("myaccesskey", "mysecret", "localhost:8998");

            // Confirm that client is valid and can connect to a database
            client.connect("hello");

            // Note that a Client is returned immediately without contacting
            // a server and can thus be invalid.
            AsyncClient client2 = AsyncDatomic.clientPeerServer("admin", "nice-try", "localhost:8998");

            // Invalid setup shows on first call to server
            Forbidden forbidden = assertThrows(
                Forbidden.class,
                () -> client2.connect("hello")
            );
            assertThat(forbidden.msg(), is("forbidden"));
            assertThat(forbidden.httpRequest().get("status"), is(403));
            assertNull(forbidden.httpRequest().get("body"));

            /*
            Example of forbidden.httpRequest data:

            Map(
              status -> 403,
              headers -> Map(
                server -> Jetty(9.3.7.v20160115),
                content-length -> 19,
                date -> Sun, 13 Sep 2020 19:14:36 GMT,
                content-type -> application/transit+msgpack
              ),
              body -> null
            )
            */

            // Wrong endpoint
            NotFound wrongEndpoint = assertThrows(
                NotFound.class,
                () -> AsyncDatomic.clientPeerServer("myaccesskey", "mysecret", "x")
                    .connect("hello")
            );
            assertThat(wrongEndpoint.msg(), is("x: nodename nor servname provided, or not known"));

        } else {
            // cloud
            // todo
        }
    }


    @Test
    public void q() {

        // query & args / String
        assertThat(
            AsyncDatomic.q(
                "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                conn.db()
            ).realize().toArray(),
            is(list(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // Input arg(s)
        assertThat(
            AsyncDatomic.q(
                "[:find ?movie-title " +
                    ":in $ ?year " +
                    ":where [?e :movie/release-year ?year]" +
                    "       [?e :movie/title ?movie-title]]",
                conn.db(), 1984L
            ).realize().toArray(),
            is(list(list("Repo Man")).toArray())
        );

        // query & args / data structure
        assertThat(
            AsyncDatomic.q(
                list(
                    read(":find"), read("?title"),
                    read(":where"), list(read("_"), read(":movie/title"), read("?title"))
                ),
                conn.db()
            ).realize().toArray(),
            is(list(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // arg-map / String
        assertThat(
            AsyncDatomic.q(
                map(
                    read(":query"), "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                    read(":args"), list(conn.db().datomicDb())
                )
            ).realize().toArray(),
            is(list(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // arg-map / data structure
        assertThat(
            AsyncDatomic.q(
                map(
                    read(":query"), list(
                        read(":find"), read("?title"),
                        read(":where"), list(read("_"), read(":movie/title"), read("?title"))
                    ),
                    read(":args"), list(conn.db().datomicDb())
                )
            ).realize().toArray(),
            is(list(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // arg-map / String with :limit
        assertThat(
            AsyncDatomic.q(
                map(
                    read(":query"), "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                    read(":args"), list(conn.db().datomicDb()),
                    read(":limit"), 2
                )
            ).realize().toArray(),
            is(list(list("Commando"), list("The Goonies")).toArray())
        );

        // arg-map / String with :offset, :limit :timeout
        assertThat(
            AsyncDatomic.q(
                map(
                    read(":query"), "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                    read(":args"), list(conn.db().datomicDb()),
                    read(":offset"), 1,
                    read(":limit"), 1,
                    read(":timeout"), 2000
                )
            ).realize().toArray(),
            is(list(list("The Goonies")).toArray())
        );
    }


    @Test
    public void qseq() {

        // query & args / String
        assertThat(
            AsyncDatomic.qseq(
                "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                conn.db()
            ).realize().toArray(),
            is(Stream.of(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // query & args / data structure
        assertThat(
            AsyncDatomic.qseq(
                list(
                    read(":find"), read("?title"),
                    read(":where"), list(read("_"), read(":movie/title"), read("?title"))
                ),
                conn.db()
            ).realize().toArray(),
            is(Stream.of(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // arg-map / String
        assertThat(
            AsyncDatomic.qseq(
                map(
                    read(":query"), "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                    read(":args"), list(conn.db().datomicDb())
                )
            ).realize().toArray(),
            is(Stream.of(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // arg-map / data structure
        assertThat(
            AsyncDatomic.qseq(
                map(
                    read(":query"), list(
                        read(":find"), read("?title"),
                        read(":where"), list(read("_"), read(":movie/title"), read("?title"))
                    ),
                    read(":args"), list(conn.db().datomicDb())
                )
            ).realize().toArray(),
            is(Stream.of(list("Commando"), list("The Goonies"), list("Repo Man")).toArray())
        );

        // arg-map / String with :limit
        assertThat(
            AsyncDatomic.qseq(
                map(
                    read(":query"), "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                    read(":args"), list(conn.db().datomicDb()),
                    read(":limit"), 2
                )
            ).realize().toArray(),
            is(Stream.of(list("Commando"), list("The Goonies")).toArray())
        );

        // arg-map / String with :offset, :limit :timeout
        assertThat(
            AsyncDatomic.qseq(
                map(
                    read(":query"), "[:find ?movie-title :where [_ :movie/title ?movie-title]]",
                    read(":args"), list(conn.db().datomicDb()),
                    read(":offset"), 1,
                    read(":limit"), 1,
                    read(":timeout"), 2000
                )
            ).realize().toArray(),
            is(Stream.of(list("The Goonies")).toArray())
        );
    }
}
