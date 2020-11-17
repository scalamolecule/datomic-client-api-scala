package datomicJava.client.api.sync;

import datomicJava.Setup;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;


public class TxReportTest extends Setup {

    public TxReportTest(String name) {
        system = name;
    }

    @Test
    public void txReportOps() {

        assertThat(films(filmDataTx.dbBefore()), is(empty()));
        assertThat(films(filmDataTx.dbAfter()), is(threeFilms));

        // Tx datom + 3 entities * 3 attributes transacted
        assertThat(filmDataTx.txData().count(), is((long) 1 + 3 * 3));

        if (isDevLocal()) {
            // No temp ids created with dev-local setup
            assertThat(filmDataTx.tempIds().size(), is(0));
        } else {
            assertThat(filmDataTx.tempIds().size(), is(3));
        }
    }
}
