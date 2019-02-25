/** Unit testing for the BTree class.
 * Requires the junit package.
 */

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class BTreeTest {
    List<String> keys = Arrays.asList("apple", "banana", "carrot", "date", "elderberry", "fungus", "grape");
    List<Integer> vals = Arrays.asList(32, 54, 98, 35, 75, 23, 66);
    Map<String,Integer> base = new TreeMap<>();

    public BTreeTest() {
        Collections.shuffle(vals);
        for (int i=0; i < keys.size() && i < vals.size(); ++i) {
            base.put(keys.get(i), vals.get(i));
        }
    }

    @Test
    public void empty() {
        BTree<String,Integer> t = new BTree<>();
        assertEquals(0, t.size());
        assertEquals(0, t.height());
        assertTrue(t.isEmpty());
        assertNull(t.get("what"));
    }

    @Test
    public void size1() {
        BTree<String,Integer> t = new BTree<>();
        assertNull(t.put("cool", 10));
        assertFalse(t.isEmpty());
        assertEquals(10, t.get("cool").intValue());
        assertNull(t.get("strange"));
        assertEquals(10, t.put("cool", 20).intValue());
        assertEquals(1, t.size());
        assertEquals(20, t.remove("cool").intValue());
        assertTrue(t.isEmpty());
    }

    @Test
    public void getset() {
        BTree<String,Integer> t = new BTree<>();
        Collections.shuffle(keys);
        for (String k : keys) {
            t.put(k, base.get(k));
        }
        assertEquals(base.size(), t.size());
        Collections.shuffle(keys);
        for (String k : keys) {
            assertEquals(base.get(k), t.get(k));
        }
        assertEquals(base.entrySet(), t.entrySet());
    }

    @Test
    public void iter() {
        BTree<String, Integer> t = new BTree<>();
        Collections.shuffle(keys);
        for (String k : keys) {
            t.put(k, base.get(k));
        }
        int count = 0;
        String prev = "";
        for (String k : t.keySet()) {
            assertTrue(prev.compareTo(k) < 0);
            assertTrue(base.containsKey(k));
            prev = k;
            ++count;
        }
        assertEquals(base.size(), count);
    }

    @Test
    public void contains() {
        BTree<String, Integer> t1 = new BTree<>();
        Collections.shuffle(keys);
        int i;
        for (i=0; i < keys.size() / 2; ++i) {
            t1.put(keys.get(i), base.get(keys.get(i)));
        }
        for (; i < keys.size(); ++i) {
            assertFalse(t1.containsKey(keys.get(i)));
        }
        BTree<String, Integer> t2 = new BTree<>();
        Collections.shuffle(keys);
        for (String k : keys) {
            t2.put(k, base.get(k));
        }
        Collections.shuffle(keys);
        for (String k : keys) {
            assertTrue(t2.containsKey(k));
        }
    }

    @Test
    public void large() {
        int n = 1000;
        Map<Long,Double> checker = new HashMap<>();
        Map<Long,Double> t = new BTree<>();
        Random r = new Random();
        Iterator<Double> viter = r.doubles(n).iterator();
        r.longs(n).forEach( k -> {
            double v = viter.next();
            checker.put(k,v);
            t.put(k,v);
        });
        assertEquals(checker.size(), t.size());

        r.longs(100).forEach( k -> {
            assertEquals(checker.get(k), t.get(k));
        });

        List<Long> ks = new ArrayList<>(checker.keySet());
        Collections.shuffle(ks);
        for (long k : ks) {
            assertEquals(checker.get(k), t.get(k));
        }

        Collections.shuffle(ks);
        for (long k : ks.subList(0, 100)) {
            assertEquals(checker.remove(k), t.remove(k));
        }
        assertEquals(checker.size(), t.size());
        assertEquals(checker, t);

        t.clear();
        assertEquals(t.size(), 0);
        assertEquals(new BTree<Long,Double>(), t);
    }
}
