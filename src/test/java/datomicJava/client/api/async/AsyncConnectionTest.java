package datomicJava.client.api.async;

import datomicClojure.ErrorMsg;
import datomicJava.SetupAsync;
import datomicJava.client.api.Datom;
import javafx.util.Pair;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Iterator;

import static datomic.Util.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

@FixMethodOrder(MethodSorters.JVM)
public class AsyncConnectionTest extends SetupAsync {

    public AsyncConnectionTest(String name) {
        system = name;
    }


    @Test
    public void db() {
        // Test if repeated calls do conn.db returns the same db value (/object)
        AsyncDb db = conn.db();

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
        if (isDevLocal()) {
            // Same db object
            assertThat(conn.sync(tAfter()).realize(), is(dbAfter()));
        } else {
            // todo? Does sync call need to create a new db object
            //  or could it be memoized/cached?
            // New db object
            assertThat(conn.sync(tAfter()).realize(), not(dbAfter()));
        }
    }


    @Test
    public void transact() {
        assertThat(films(conn.db()), is(threeFilms));
        conn.transact(list(
            map(
                read(":movie/title"), "Film 4"
            )
        ));
        assertThat(films(conn.db()), is(fourFilms));

        IllegalArgumentException emptyTx = assertThrows(
            IllegalArgumentException.class,
            () -> conn.transact(list())
        );
        assertThat(
            emptyTx.getMessage(),
            is(ErrorMsg.transact())
        );
    }


    @Test
    public void txRange() {
        // Lazy retrieval with Iterable
        final Iterator<Pair<Object, Iterable<Datom>>> it =
            conn.txRange().realize().iterator();
        Pair<Object, Iterable<Datom>> lastTx = it.next();
        while (it.hasNext()) {
            lastTx = it.next();
        }
        assertThat(lastTx.getKey(), is(tAfter()));
        assertThat(lastTx.getValue().iterator().next(), is(
            new Datom(txIdAfter(), 50, txInst(), txIdAfter(), true)
        ));

        // Array
        final Pair[] it2 = conn.txRangeArray().realize();
        Pair<Object, Datom[]> lastTx2 = (Pair<Object, Datom[]>) it2[it2.length - 1];
        assertThat(lastTx2.getKey(), is(tAfter()));
        assertThat(lastTx2.getValue()[0], is(
            new Datom(txIdAfter(), 50, txInst(), txIdAfter(), true)
        ));
    }
}