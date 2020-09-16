package datomicJava.client.api.sync;

import datomicJava.client.api.Datom;
import javafx.util.Pair;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Iterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@FixMethodOrder(MethodSorters.JVM)
public class ConnectionTest extends Setup {

    public ConnectionTest(String name) {
        system = name;
    }

    @Test
    public void txRange() {
        // Lazy retrieval with Iterable
        final Iterator<Pair<Object, Iterable<Datom>>> it = conn.txRange().iterator();
        Pair<Object, Iterable<Datom>> lastTx = it.next();
        while (it.hasNext()) {
            lastTx = it.next();
        }
        assertThat(lastTx.getKey(), is(tAfter()));
        assertThat(lastTx.getValue().iterator().next(), is(
            new Datom(txIdAfter(), 50, txInst(), txIdAfter(), true)
        ));

        // Array
        final Pair[] it2 = conn.txRangeArray();
        Pair<Object, Datom[]> lastTx2 = (Pair<Object, Datom[]>) it2[it2.length - 1];
        assertThat(lastTx2.getKey(), is(tAfter()));
        assertThat(lastTx2.getValue()[0], is(
            new Datom(txIdAfter(), 50, txInst(), txIdAfter(), true)
        ));
    }


    @Test
    public void db() {
        // Test if repeated calls do conn.db returns the same db value (/object)
        Db db = conn.db();

        if (isDevLocal()) {
            // Dev-local connection returns same database object
            assertThat(conn.db(), is(db));
        } else {
            // Peer Server connection returns new database object
            assertThat(conn.db(), not(db));
        }
    }


    @Test
    public void sync() {
        // Db value the same
        assertThat(conn.sync(tAfter()).equals(dbAfter()), is(true));
        assertThat(conn.sync(tAfter()), is(dbAfter()));

        // Db object identity
        if (isDevLocal()) {
            // Same db object
            assertThat(conn.sync(tAfter()), is(dbAfter()));
        } else {
            // Db object copy
            assertThat(conn.sync(tAfter()), not(dbAfter()));
        }
    }
}
