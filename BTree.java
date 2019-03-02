/******************************************************************************
 * B-tree with pre-emptive splitting.
 * Dan Roche, 2019. roche@usna.edu
 *
 * This follows the requirements for a modifiable map based on AbstractMap.
 *
 * null values are _not_ allowed; setting a value to null is equivalent
 * to removing it from the map.
 *
 * Internally, a B-tree is stored as a tree of Node objects, each of which
 * contains a list of key/payload pairs plus one additional "left" payload.
 *
 * For internal nodes, the payload is a child node, and the "left" payload
 * is the leftmost child of that internal node in the tree.
 *
 * For leaf nodes, the payload is a value in the map, type V, and the "left"
 * payload is the value associated to some key in an internal node ancestor;
 * in particular, the value for the predecessor key in the tree.
 *
 * Due to this structure, all values are stored in the leaf nodes, but there
 * is no duplication of keys nor of values in the entire tree.
 *
 * The ordering is according to the natural order of the keys, and the payload
 * for each internal node's index i contains the subtree to the right of that
 * node's key at index i.
 * More precisely, for any InternalNode u and index i, each key within
 * u.get(i).getValue() is between u.get(i-1).getKey() and u.get(i).getKey().
 * Each key within u.getLeft() is less than u.get(0).getKey().
 *
 * Removing the Value mapped to a given Key is supported from an interface
 * perspective, but internally this doesn't really change the tree, instead
 * just setting the corresponding Value to null.
 *
 * Inserting a new Key/Value pair utilizes "pre-emptive splitting", in which
 * full Nodes are split "on the way down" in the search path through the tree.
 * As a result, insertion only involves a single traversal down the tree and
 * O(1) temporary storage.
 ******************************************************************************/
public class BTree<K extends Comparable<? super K>, V> extends java.util.AbstractMap<K,V> {
    /** Maximum number of key/value pairs in a leaf node. */
    public static final int LeafM = 4;
    /** Maximum number of key/child pairs in an internal node. */
    public static final int InternalM = 4;

    /** A pseudo-entry whose key is in an internal node and whose value is in a leaf node. */
    private static class InternalEntry<K extends Comparable<? super K>,V> implements Entry<K,V> {
        private static final long serialVersionUID = 1L;

        private K key;
        private LeafNode<K,V> leaf;

        public InternalEntry(K key, LeafNode<K,V> leaf) {
            this.key = key;
            this.leaf = leaf;
        }

        @Override
        public K getKey() { return key; }

        @Override
        public V getValue() { return leaf.getLeft(); }

        @Override
        public V setValue(V newval) { return leaf.setLeft(newval); }
    }

    /** A single node in the B-tree, containing keys and payloads.
     * The payload type P should either be V for leaf nodes or Node for internal nodes.
     */
    private static abstract class Node<K extends Comparable<? super K>,V,P>
        extends java.util.ArrayList<Entry<K,P>>
    {
        private static final long serialVersionUID = 1L;

        /** holds either the parent value or the leftmost child */
        private P left;

        /** Creates a new node with the given left payload and key/payload pairs. */
        protected Node(int maxsize, P left, java.util.Collection<? extends Entry<K,P>> entries) {
            super(maxsize);
            this.left = left;
            addAll(entries);
        }

        public P getLeft() { return left; }
        public P setLeft(P newleft) {
            P old = left;
            left = newleft;
            return old;
        }

        abstract public boolean isFull();

        /** Inserts the given payload at the specified index, moving others over. */
        @Override
        public void add(int ind, Entry<K,P> entry) {
            assert ! this.isFull();
            assert ind == 0 || entry.getKey().compareTo(get(ind-1).getKey()) > 0;
            assert ind == size() || entry.getKey().compareTo(get(ind).getKey()) < 0;
            super.add(ind, entry);
        }

        /** Creates a new entry from the given key/value pair and inserts it at the given index. */
        public Entry<K,P> insert(int ind, K key, P payload) {
            Entry<K,P> entry = new SimpleEntry<>(key, payload);
            add(ind, entry);
            return entry;
        }

        /** Returns the index of needle, or (-ind+1) is the index where needle should be. */
        protected int getInd(Comparable<? super K> needle) {
            return MyCollections.binarySearch(this,
                (Entry<K,?> o) -> needle.compareTo(o.getKey()));
        }

        /** Searches for the given needle in this node or below.
         * @return the matching key/value pair, or null if not found.
         */
        abstract public Entry<K,V> search(Comparable<? super K> needle);

        /** Similar to search, but never returns null.
         * This will split notes as needed and insert a new entry into a leaf node if necessary.
         * Prerequisite: this node must not be full.
         */
        abstract public Entry<K,V> searchInsert(K needle);

        /** Creates an Entry view of the given key by following left children to a leaf node. */
        abstract protected InternalEntry<K,V> leftEntry(K key);

        /** Creates a new sibling node based on the given payload and list of entries. */
        abstract protected Node<K,V,P> spawn(P payload, java.util.Collection<? extends Entry<K,P>> entries);

        /** Splits this node and returns the to-be-promoted entry. */
        public Entry<K,Node<K,V,?>> split() {
            assert isFull();
            int mid = size() / 2;
            K promoted = get(mid).getKey();
            Node<K,V,P> sibling = spawn(get(mid).getValue(), subList(mid+1, size()));
            subList(mid, size()).clear();
            return new SimpleEntry<>(promoted, sibling);
        }

        /** Pushes iterators for this node and internal nodes below, then returns leftmost leaf.
         * Used internally by the iterator for the BTree map.
         */
        abstract public LeafNode<K,V> iterPush(java.util.Deque<java.util.Iterator<Entry<K,Node<K,V,?>>>> stack);
    }

    private static class LeafNode<K extends Comparable<? super K>,V>
        extends Node<K,V,V>
    {
        private static final long serialVersionUID = 1L;
        public static final int M = BTree.LeafM;

        /** Creates a new sibling leaf node with the given parent value and key/value pairs. */
        public LeafNode(V parentVal, java.util.Collection<? extends Entry<K,V>> entries)
        { super(M, parentVal, entries); }

        @Override
        public boolean isFull()
        { return this.size() == M; }

        @Override
        public Entry<K,V> search(Comparable<? super K> needle) {
            int ind = getInd(needle);
            if (ind >= 0) return get(ind);
            else return null;
        }

        @Override
        public Entry<K,V> searchInsert(K needle) {
            assert !isFull();
            int ind = getInd(needle);
            if (ind >= 0) return get(ind);
            else return insert(-ind-1, needle, null);
        }

        @Override
        protected InternalEntry<K,V> leftEntry(K key) {
            return new InternalEntry<>(key, this);
        }

        @Override
        protected LeafNode<K,V> spawn(V payload, java.util.Collection<? extends Entry<K,V>> entries) {
            return new LeafNode<>(payload, entries);
        }

        @Override
        public LeafNode<K,V> iterPush(java.util.Deque<java.util.Iterator<Entry<K,Node<K,V,?>>>> stack) {
            return this;
        }
    }

    private static class InternalNode<K extends Comparable<? super K>,V>
        extends Node<K,V,Node<K,V,?>>
    {
        private static final long serialVersionUID = 1L;
        public static final int M = BTree.InternalM;

        /** Creates a new sibling node with the given left child and list of key/children pairs. */
        public InternalNode(Node<K,V,?> leftChild, java.util.Collection<? extends Entry<K,Node<K,V,?>>> entries)
        { super(M, leftChild, entries); }

        @Override
        public boolean isFull()
        { return this.size() == M; }

        @Override
        public Entry<K,V> search(Comparable<? super K> needle) {
            int ind = getInd(needle);
            if (ind >= 0)
                return get(ind).getValue().leftEntry(get(ind).getKey());
            else if (ind == -1)
                return getLeft().search(needle);
            else return get(-ind-2).getValue().search(needle);
        }

        @Override
        public Entry<K,V> searchInsert(K needle) {
            assert !isFull();
            int ind = getInd(needle);
            if (ind >= 0) return get(ind).getValue().leftEntry(get(ind).getKey());

            Node<K,V,?> child = (ind == -1) ? getLeft() : get(-ind-2).getValue();
            int insInd = -ind - 1;

            if (child.isFull()) {
                Entry<K,Node<K,V,?>> newEnt = child.split();
                K promoted = newEnt.getKey();
                Node<K,V,?> newChild = newEnt.getValue();
                add(insInd, newEnt);
                int cmp = needle.compareTo(promoted);
                if (cmp == 0) return newChild.leftEntry(promoted);
                else if (cmp > 0) child = newChild;
            }

            return child.searchInsert(needle);
        }

        @Override
        protected InternalEntry<K,V> leftEntry(K key)
        { return getLeft().leftEntry(key); }

        @Override
        protected InternalNode<K,V> spawn
            (Node<K,V,?> payload,
             java.util.Collection<? extends Entry<K,Node<K,V,?>>> entries)
        {
            return new InternalNode<>(payload, entries);
        }

        @Override
        public LeafNode<K,V> iterPush(java.util.Deque<java.util.Iterator<Entry<K,Node<K,V,?>>>> stack) {
            stack.addLast(iterator());
            return getLeft().iterPush(stack);
        }
    }

    private Node<K,V,?> root = new LeafNode<>(null, java.util.Collections.emptyList());
    private int ht = 0;
    private int sz = 0;

    @Override
    public boolean isEmpty() { return this.sz == 0; }

    @Override
    public int size() { return this.sz; }

    public int height() { return this.ht; }

    @Override
    public void clear() {
      this.root = new LeafNode<>(null, java.util.Collections.emptyList());
      this.sz = 0;
      this.ht = 0;
    }

    // annoying unchecked conversion because Map specifies the key has type Object
    @SuppressWarnings("unchecked")
    private Entry<K,V> search(Object key) {
        return root.search((Comparable<? super K>) key);
    }

    @Override
    public boolean containsKey(Object key) {
        return search(key) != null;
    }

    @Override
    public V get(Object key) {
        Entry<K,V> found = search(key);
        if (found == null) return null;
        else return found.getValue();
    }

    @Override
    public V put(K key, V value) {
        // putting a null value is the same as removal, but removal is faster (no splitting)
        if (value == null) return this.remove(key);

        // if the root is full, split it to get a new root
        if (root.isFull()) {
            root = new InternalNode<>(root, java.util.Collections.singleton(root.split()));
        }

        Entry<K,V> found = root.searchInsert(key);
        V old = found.setValue(value);
        if (old == null) ++this.sz;
        return old;
    }

    // equivalent to put(key, null) but avoids splits.
    @Override
    public V remove(Object key) {
        Entry<K,V> found = search(key);
        if (found == null) return null;

        V old = found.setValue(null);
        if (old != null) --this.sz;
        return old;
    }

    /** A class to view the key/value pair entries of the B-tree as a Set.
     * Required by AbstractMap.
     */
    public class SetView extends java.util.AbstractSet<Entry<K,V>> {
        @Override
        public int size() { return BTree.this.size(); }

        @Override
        public java.util.Iterator<Entry<K,V>> iterator() {
            return BTree.this.new Iter();
        }

        @Override
        public void clear() { BTree.this.clear(); }

        @Override
        public boolean isEmpty() { return BTree.this.isEmpty(); }
    }

    /** A class to walk through all non-null entries in the B-tree, in order.
     * Iterators are invalidated whenever a modification is made (via a call to
     * put, for example) other than calling remove() on the iterator itself.
     */
    public class Iter implements java.util.Iterator<Entry<K,V>> {
        // The stack stores an iterator for the items in a node of each internal level of the tree.
        private java.util.Deque<java.util.Iterator<Entry<K,Node<K,V,?>>>> stack = null;
        private java.util.Iterator<Entry<K,V>> stackTop = null;
        // prev is the entry that was just returned, next is the one that will be returned.
        private Entry<K,V> prev = null;
        private Entry<K,V> next = null;

        /** Creates a new instance attached to the containin BTree class. */
        public Iter() {
            if (isEmpty()) return;

            stack = new java.util.ArrayDeque<>(height()-1);
            stackTop = root.iterPush(stack).iterator();
            advance();
        }

        // advances to the next non-null entry, so prev will get the current value of next,
        // and next will point to the next non-null entry in the tree.
        private void advance() {
            prev = next;

            do {
                if (!stackTop.hasNext()) {
                    stackTop = null;
                    while (!stack.getLast().hasNext()) {
                        stack.removeLast();
                        if (stack.isEmpty()) {
                            next = null;
                            return;
                        }
                    }
                    Entry<K,Node<K,V,?>> internal = stack.getLast().next();
                    LeafNode<K,V> leaf = internal.getValue().iterPush(stack);
                    stackTop = leaf.iterator();
                    next = leaf.leftEntry(internal.getKey());
                }
                else next = stackTop.next();
            } while (next.getValue() == null);
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Entry<K,V> next() {
            if (next == null)
                throw new java.util.NoSuchElementException("end of the B-tree");
            advance();
            return prev;
        }

        @Override
        public void remove() {
            if (prev == null) throw new IllegalStateException("next never called");
            if (prev.setValue(null) != null) --BTree.this.sz;
        }
    }

    @Override
    public SetView entrySet() { return this.new SetView(); }
}

class MyCollections {
    /** Binary search for a comparable key in an arbitrary list.
     *
     * Modified from the OpenJDK8 implementation of binarySearch from
     * https://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/src/share/classes/java/util/Collections.java
     *
     * This version changes the type requirements so that the key is Comparable but the list
     * elements are not. This is the opposite of what is in the standard java.util.Collections.
     */
    public static <T>
    int binarySearch(java.util.List<T> list, Comparable<? super T> key) {
        int low = 0;
        int high = list.size()-1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = list.get(mid);
            int cmp = key.compareTo(midVal);

            if (cmp > 0)
                low = mid + 1;
            else if (cmp < 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found
    }
}
