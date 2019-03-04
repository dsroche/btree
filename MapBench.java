public class MapBench {
    private int iters;
    private java.util.Random rgen;
    private java.util.List<java.util.Map.Entry<Integer,Long>> contents;
    private java.util.List<Integer> found;
    private java.util.List<Integer> rand;

    public MapBench(int iters, java.util.Random rgen) {
        this.iters = iters;
        this.rgen = rgen;
        contents = new java.util.ArrayList<>(iters);
        found = new java.util.ArrayList<>(iters);
        rand = new java.util.ArrayList<>(iters);
        genContents();
    }

    public MapBench(int iters)
    { this(iters, new java.util.Random()); }

    public MapBench(int iters, long seed)
    { this(iters, new java.util.Random(seed)); }

    public void genContents() {
        contents.clear();
        for (int i=0; i<iters; ++i) {
            contents.add(new java.util.AbstractMap.SimpleEntry<>(rgen.nextInt(), rgen.nextLong()));
        }
        found.clear();
        for (java.util.Map.Entry<Integer,Long> item : contents) {
            found.add(item.getKey());
        }
        java.util.Collections.shuffle(found, rgen);
        rand.clear();
        for (int i=0; i<iters; ++i) {
            rand.add(rgen.nextInt());
        }
    }

    public void insert(java.util.Map<Integer,Long> map) {
        for (java.util.Map.Entry<Integer,Long> item : contents) {
            map.put(item.getKey(), item.getValue());
        }
    }

    public long getFound(java.util.Map<Integer,Long> map) {
        long result = 0;
        for (int k : found) {
            result ^= map.get(k);
        }
        return result;
    }

    public int getRand(java.util.Map<Integer,Long> map) {
        int count = 0;
        for (int k : rand) {
            if (map.containsKey(k)) ++count;
        }
        return count;
    }

    public void bench(java.util.Map<Integer,Long> map) {
        System.out.println("=========== TIMING " + map.getClass().getSimpleName() + " ==============");
        long[] fres = new long[3];
        int[] rres = new int[3];

        System.out.println("Warmup...");
        map.clear();
        insert(map);
        fres[0] = getFound(map);
        rres[0] = getRand(map);

        System.out.println("Timing...");
        map.clear();
        long start;
        double[] times = new double[]{0,0,0};
        start = System.nanoTime();
        insert(map);
        times[0] = System.nanoTime() - start;
        start = System.nanoTime();
        fres[1] = getFound(map);
        times[1] = System.nanoTime() - start;
        start = System.nanoTime();
        rres[1] = getRand(map);
        times[2] = System.nanoTime() - start;
        System.out.println("insertion: " + times[0]/iters);
        System.out.println("retrieval: " + times[1]/iters);
        System.out.println("   lookup: " + times[2]/iters);

        System.out.println("Cool down...");
        map.clear();
        insert(map);
        fres[2] = getFound(map);
        rres[2] = getRand(map);
        if (fres[0] != fres[1] || fres[0] != fres[2]) {
            System.out.println("ERROR ERROR fres = " + java.util.Arrays.toString(fres));
        }
        if (rres[0] != rres[1] || rres[0] != rres[2]) {
            System.out.println("ERROR ERROR rres = " + java.util.Arrays.toString(rres));
        }
        System.out.println("check: " + fres[0] + " " + rres[0]);
        System.out.println();
    }

    public static void main(String[] args) {
        MapBench b;
        if (args.length == 0) b = new MapBench(1000000);
        else if (args.length == 1) b = new MapBench(Integer.valueOf(args[0]));
        else b = new MapBench(Integer.valueOf(args[0]), Long.valueOf(args[1]));

        b.bench(new BTree<>());
        b.bench(new java.util.TreeMap<>());
        b.bench(new java.util.HashMap<>());
    }
}
