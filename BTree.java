/******************************************************************************
 * B-tree with pre-emptive splitting.
 * Dan Roche, 2019. roche@usna.edu
 *
 * This follows the requirements for a modifiable map based on AbstractMap.
 *
 * null values are _not_ allowed; setting a value to null is equivalent
 * to removing it from the map.
 *
 * Internally, a B-tree is stored as a tree of Node objects. Each Node consists
 * mostly of a list of Entry objects. In LeafNode's, each Entry is simply
 * a Key/Value pair. In InternalNode's, each Entry additionally contains a
 * pointer to the child node. This corresponds to the *right child* underneath
 * that entry. Separately, InternalNode's store a single pointer to the leftmost
 * child. Unlike some B-tree implementations, Key/Value pairs are stored both
 * in internal and in leaf nodes, without duplication, so that in particular the
 * leaf level does not contain every key/value pair in the tree.
 *
 * More precisely, for any InternalNode u and index i, each key within
 * u.items[i].child is between u.items[i-1].key and u.items[i].key.
 * Each key within u.leftChild is less than u.items[0].key.
 *
 * Removing the Value mapped to a given Key is supported from an interface
 * perspective, but internally this doesn't really change the tree, instead
 * just setting the corresponding Value to null.
 *
 * Inserting a new Key/Value pair utilizes "pre-emptive splitting", in which
 * full Nodes are split "on the way down" in the search path through the tree.
 * As a result, insertion only involves a single traversal down the tree and
 * O(1) temporary storage.
 *
 ******************************************************************************/
public class BTree<K extends Comparable<? super K>, V> extends java.util.AbstractMap<K,V> {
    /** Maximum number of key/value pairs in a leaf node. */
    public static final int LeafM = 6;
    /** Maximum number of key/value pairs in an internal node.
     * Should be roughly 2/3 of LeafM for the same actual node size. */
    public static final int InternalM = 4;

    /** A key/value pair entry in a leaf node. */
    private static class LeafEntry<K,V> extends java.util.AbstractMap.SimpleEntry<K,V> {
        /** prevent warning since SimpleEntry is serializable. */
        private static final long serialVersionUID = 1L;

        public LeafEntry(K key, V value)
        { super(key, value); }

        /** The right child of this internal node, or null for a leaf node. */
        public Node<K,V,?> rightChild() { return null; }
    }

    /** A key/value/child triple in an internal node.
     * Note that the first (leftmost) child is stored separately.
     */
    private static class InternalEntry<K,V> extends LeafEntry<K,V> {
        /** prevent warning since SimpleEntry is serializable. */
        private static final long serialVersionUID = 1L;

        private Node<K,V,?> child;

        public InternalEntry(java.util.Map.Entry<K,V> parent, Node<K,V,?> child) {
            super(parent.getKey(), parent.getValue());
            this.child = child;
        }

        /** The right child of this internal node. */
        public Node<K,V,?> rightChild() { return this.child; }
    }

    /** Base class for leaf and internal nodes.
     * Mostly this consists of a list of either LeafEntry or InternalEntry objects,
     * depending on whether it is a leaf or internal node.
     */
    abstract private static class Node<K,V,E extends LeafEntry<K,V>>
            extends java.util.ArrayList<E>
    {
        /** prevent warning since SimpleEntry is serializable. */
        private static final long serialVersionUID = 1L;

        protected Node(int maxsize) {
            super(maxsize);
        }

        abstract public boolean isLeaf();

        abstract public boolean isFull();

        /** Inserts the given entry at the specified index, moving others over. */
        @Override
        public void add(int ind, E entry) {
            assert ! this.isFull();
            super.add(ind, entry);
        }

        /** Inserts the given key/value pair in a leaf node. */
        public void insert(int ind, K key, V value) {
            throw new UnsupportedOperationException("insertions can only happen in leaf nodes");
        }

        /** Searches for needle in the entries' keys and returns the corresponding index.
         * If the needle is not found, a negative index i is returned such that
         * - the needle would be inserted before index (-i - 1)
         * - the needle would go in child index (-i - 2)
         */
        public int search(Comparable<? super K> needle) {
            return MyCollections.binarySearch(this,
                (java.util.Map.Entry<K,?> o) -> needle.compareTo(o.getKey()));
        }

        /** Similar to search, but preemtively splits the corresponding child node.
         * If this is an internal node and the needle is not found within the
         * node itself, it is guaranteed that the child where the needle belongs,
         * at index (-i - 2), is not full when this method returns.
         */
        abstract public int searchAndSplit(Comparable<? super K> needle);

        /** Retrieves the child node at the given index, starting at -1.
         * It is guaranteed that all keys in child node at index i
         * fall between getKey(i-1) and getKey(i).
         */
        abstract public Node<K,V,?> getChild(int ind);

        /** Creates a new sibling node based on the given list of entries. */
        abstract protected Node<K,V,E> splitRight(Node<K,V,?> middleChild,
                java.util.Collection<E> after);

        /** Splits this (full) node into two.
         * @return a new entry to be added to the parent.
         */
        public InternalEntry<K,V> split() {
            assert isFull();
            int mid = size() / 2;
            InternalEntry<K,V> parent = new InternalEntry<>(
                get(mid),
                splitRight(
                    get(mid).rightChild(),
                    subList(mid+1, size())
                )
            );
            subList(mid, size()).clear();
            return parent;
        }
    }

    private static class LeafNode<K,V> extends Node<K,V,LeafEntry<K,V>>
    {
        /** prevent warning since SimpleEntry is serializable. */
        private static final long serialVersionUID = 1L;

        public LeafNode() {
            super(BTree.LeafM);
        }

        @Override
        public boolean isLeaf() { return true; }

        @Override
        public boolean isFull() {
            return this.size() == BTree.LeafM;
        }

        @Override
        public void insert(int ind, K key, V value) {
            add(ind, new LeafEntry<>(key, value));
        }

        @Override
        public int searchAndSplit(Comparable<? super K> needle) {
            // no pre-emptive splitting to do in leaf nodes
            return search(needle);
        }

        @Override
        public Node<K,V,?> getChild(int ind) { return null; }

        @Override
        protected LeafNode<K,V> splitRight(Node<K,V,?> middleChild,
                java.util.Collection<LeafEntry<K,V>> after)
        {
            assert middleChild == null;
            LeafNode<K,V> right = new LeafNode<>();
            right.addAll(after);
            return right;
        }
    }

    private static final class InternalNode<K,V> extends Node<K,V,InternalEntry<K,V>> {
        /** prevent warning since SimpleEntry is serializable. */
        private static final long serialVersionUID = 1L;

        private Node<K,V,?> leftChild;

        public InternalNode(Node<K,V,?> left) {
            super(BTree.InternalM);
            leftChild = left;
        }

        @Override
        public boolean isLeaf() { return false; }

        @Override
        public boolean isFull() {
            return this.size() == BTree.InternalM;
        }

        @Override
        public int searchAndSplit(Comparable<? super K> needle) {
            // do a regular search, and return index if found
            int sind = search(needle);
            if (sind >= 0) return sind;

            // lookup child and return search index if it is not full
            int cind = -sind - 2;
            Node<K,V,?> curChild = getChild(cind);
            if (!curChild.isFull()) return sind;

            // split the full child, then compare with promoted key to get final result
            this.add(cind+1, curChild.split());
            int cmp = needle.compareTo(get(cind+1).getKey());
            if (cmp == 0) return cind+1; // found here, so return positive key index
            else if (cmp < 0) return sind; // not found, return negative child index
            else return sind-1; // not found, return negative child index
        }

        @Override
        public Node<K,V,?> getChild(int ind) {
            if (ind == -1) return leftChild;
            else return get(ind).rightChild();
        }

        @Override
        protected InternalNode<K,V> splitRight(Node<K,V,?> middleChild,
                java.util.Collection<InternalEntry<K,V>> after)
        {
            InternalNode<K,V> right = new InternalNode<>(middleChild);
            right.addAll(after);
            return right;
        }
    }

    private Node<K,V,?> root = new LeafNode<K,V>();
    private int ht = 0;
    private int sz = 0;

    @Override
    public boolean isEmpty() { return this.sz == 0; }

    @Override
    public int size() { return this.sz; }

    public int height() { return this.ht; }

    @Override
    public void clear() {
      this.root = new LeafNode<>();
      this.sz = 0;
      this.ht = 0;
    }

    protected LeafEntry<K,V> search(Comparable<? super K> needle) {
        Node<K,V,?> cur = this.root;

        // null is returned by getChild in the leaf nodes
        while (cur != null) {
            int ind = cur.search(needle);
            // positive index means it was found
            if (ind >= 0) return cur.get(ind);

            // negative index calculation to get the child index if not found
            cur = cur.getChild(-ind - 2);
        }

        // if you get here, it means the key was not found in any node
        return null;
    }

    protected LeafEntry<K,V> search(Object key) {
        // annoying unchecked conversion because Map specifies the key has type Object
        @SuppressWarnings("unchecked")
        Comparable<? super K> needle = (Comparable<? super K>) key;
        return search(needle);
    }

    @Override
    public boolean containsKey(Object key) {
        return search(key) != null;
    }

    @Override
    public V get(Object key) {
        LeafEntry<K,V> found = search(key);
        if (found == null) return null;
        else return found.getValue();
    }

    @Override
    public V put(K key, V value) {
        // putting a null value is the same as removal, but removal is faster (no splitting)
        if (value == null) return this.remove(key);

        // check if we need to split the root
        if (this.root.isFull()) {
            InternalNode<K,V> newRoot = new InternalNode<>(this.root);
            newRoot.add(this.root.split());
            this.root = newRoot;
            ++this.ht;
        }

        Node<K,V,?> cur = this.root;

        // not an infinite loop, because it will eventually get to a leaf node
        while (true) {
            assert ! cur.isFull();

            int ind = cur.searchAndSplit(key);
            if (ind >= 0) {
                // found it, just replace
                V old = cur.get(ind).setValue(value);
                if (old == null) ++this.sz;
                return old;
            }

            // negative index calculation if not found
            ind = -ind - 1;

            if (cur.isLeaf()) {
                // insertion can happen directly in the leaf node
                cur.insert(ind, key, value);
                ++this.sz;
                return null;
            }

            cur = cur.getChild(ind - 1); // note, child indices start from -1
        }
    }

    // same as put(key, null) but avoids splits.
    @Override
    public V remove(Object key) {
        LeafEntry<K,V> found = search(key);
        if (found == null) return null;

        V old = found.setValue(null);
        if (old != null) --this.sz;
        return old;
    }

    /** A class to view the key/value pair entries of the B-tree as a Set.
     * Required by AbstractMap.
     */
    public class SetView extends java.util.AbstractSet<java.util.Map.Entry<K,V>> {
        @Override
        public int size() { return BTree.this.size(); }

        @Override
        public java.util.Iterator<java.util.Map.Entry<K,V>> iterator() {
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
    public class Iter implements java.util.Iterator<java.util.Map.Entry<K,V>> {
        // The stack stores an iterator for the items in a node of each level of the tree.
        private java.util.Deque<java.util.Iterator<? extends LeafEntry<K,V>>> stack;
        // prev is the entry that was just returned, next is the one that will be returned.
        private LeafEntry<K,V> prev = null;
        private LeafEntry<K,V> next = null;

        /** Creates a new instance attached to the containin BTree class. */
        public Iter() {
            stack = new java.util.ArrayDeque<>(BTree.this.height());

            if (BTree.this.isEmpty()) return;

            Node<K,V,?> cur = BTree.this.root;
            while (cur != null) {
                stack.addLast(cur.iterator());
                cur = cur.getChild(-1);
            }

            next = stack.getLast().next();
            if (next.getValue() == null) advance();
        }

        // advances to the next non-null entry, so prev will get the current value of next,
        // and next will point to the next non-null entry in the tree.
        private void advance() {
            if (next == null)
                throw new java.util.NoSuchElementException("end of the B-tree");

            prev = next;

            do {
                Node<K,V,?> child = next.rightChild();

                // go down the tree if there are children
                while (child != null) {
                    stack.addLast(child.iterator());
                    child = child.getChild(-1);
                }

                // go back up the tree when you get to the end of a node
                while (!stack.getLast().hasNext()) {
                    stack.removeLast();
                    if (stack.isEmpty()) {
                        next = null;
                        return;
                    }
                }

                next = stack.getLast().next();
            } while (next.getValue() == null);
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public java.util.Map.Entry<K,V> next() {
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
