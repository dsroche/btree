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
    private static class BTEntry<K,V> extends java.util.AbstractMap.SimpleEntry<K,V> {
        /** prevent warning since SimpleEntry is serializable. */
        public static final long serialVersionUID = 1L;

        public BTEntry(K key, V value)
        { super(key, value); }

        /** The right child of this internal node, or null for a leaf node. */
        public Node<K,V> rightChild() { return null; }
    }

    /** A key/value/child triple in an internal node.
     * Note that the first (leftmost) child is stored separately.
     */
    private static class InternalEntry<K,V> extends BTEntry<K,V> {
        /** prevent warning since SimpleEntry is serializable. */
        public static final long serialVersionUID = 1L;

        private Node<K,V> child;

        public InternalEntry(java.util.Map.Entry<K,V> parent, Node<K,V> child) {
            super(parent.getKey(), parent.getValue());
            this.child = child;
        }

        /** The right child of this internal node. */
        public Node<K,V> rightChild() { return this.child; }
    }

    /** Base class for leaf and internal nodes.
     * Mostly this consists of a list of either BTEntry or InternalEntry objects,
     * depending on whether it is a leaf or internal node.
     */
    abstract private static class Node<K,V> {
        private final int M;
        private java.util.List<BTEntry<K,V>> items;

        public Node(int maxitems) {
            this.M = maxitems;
            this.items = new java.util.ArrayList<>(maxitems);
        }

        public Node(int maxitems, java.util.Collection<? extends BTEntry<K,V>> c) {
            this(maxitems);
            this.items.addAll(c);
        }

        public BTEntry<K,V> getEntry(int ind) { return items.get(ind); }
        public K getKey(int ind) { return items.get(ind).getKey(); }
        public V getValue(int ind) { return items.get(ind).getValue(); }

        /** Searches for needle in the entries' keys and returns the corresponding index.
         * If the needle is not found, a negative index i is returned such that
         * - the needle would be inserted before index (-i - 1)
         * - the needle would go in child index (-i - 2)
         */
        public int search(Comparable<? super K> needle) {
            return MyCollections.binarySearch(this.items,
                (java.util.Map.Entry<K,?> o) -> needle.compareTo(o.getKey()));
        }

        /** Similar to search, but preemtively splits the corresponding child node.
         * If this is an internal node and the needle is not found within the
         * node itself, it is guaranteed that the child where the needle belongs,
         * at index (-i - 2), is not full when this method returns.
         */
        public int searchAndSplit(Comparable<? super K> needle) {
            return search(needle);
        }

        /** Inserts the given entry at the specified index, moving others over. */
        public void insert(int ind, BTEntry<K,V> entry) {
            assert ! this.isFull();
            this.items.add(ind, entry);
        }

        /** Inserts the given key/value pair at the specified index.
         * This is supported only for leaf nodes.  */
        public void insert(int ind, K key, V value) {
            throw new UnsupportedOperationException("insert key/val only in leaf nodes");
        }

        /** Replaces the value in the key/value pair at the given index.
         * The previous value at that index is returned.
         */
        public V replaceValue(int ind, V value) {
            return this.items.get(ind).setValue(value);
        }

        public boolean isFull() {
            return this.items.size() == this.M;
        }

        abstract public boolean isLeaf();

        /** Retrieves the child node at the given index, starting at -1.
         * It is guaranteed that all keys in child node at index i
         * fall between getKey(i-1) and getKey(i).
         */
        public Node<K,V> getChild(int ind) {
            return null;
        }

        /** Creates a new sibling node based on the given list of entries. */
        abstract protected Node<K,V> splitRight(BTEntry<K,V> middle,
                java.util.Collection<? extends BTEntry<K,V>> after);

        /** Splits this (full) node into two.
         * @return a new entry to be added to the parent.
         */
        public InternalEntry<K,V> split() {
            assert this.isFull();
            int mid = this.M / 2;
            InternalEntry<K,V> right = new InternalEntry<>(
                items.get(mid),
                splitRight(items.get(mid), items.subList(mid+1, items.size()))
            );
            items.subList(mid, items.size()).clear();
            return right;
        }

        /** Iterates over the contents of the node, not including the leftmost child.
         * Used by the iterator of SetView when treating the BTree as a set of entries.
         */
        public java.util.Iterator<BTEntry<K,V>> iterator() {
            return items.iterator();
        }
    }

    private static class LeafNode<K,V> extends Node<K,V> {
        public LeafNode() {
            super(BTree.LeafM);
        }

        public LeafNode(java.util.Collection<? extends BTEntry<K,V>> c) {
            super(BTree.LeafM, c);
        }

        @Override
        public boolean isLeaf() { return true; }

        @Override
        public void insert(int ind, K key, V value) {
            insert(ind, new BTEntry<K,V>(key, value));
        }

        @Override
        protected LeafNode<K,V> splitRight(BTEntry<K,V> middle,
                java.util.Collection<? extends BTEntry<K,V>> after)
        { return new LeafNode<K,V>(after); }
    }

    private static final class InternalNode<K,V> extends Node<K,V> {
        private Node<K,V> leftChild;

        /** Creates a new internal node with the given left child and a single entry. */
        public InternalNode(Node<K,V> left, InternalEntry<K,V> right) {
            super(BTree.InternalM);
            this.leftChild = left;
            this.insert(0, right);
        }

        /** Creates a new internal node with the given left child and multiple entries. */
        public InternalNode(Node<K,V> left, java.util.Collection<? extends BTEntry<K,V>> c) {
            super(BTree.InternalM, c);
            this.leftChild = left;
        }

        @Override
        public boolean isLeaf() { return false; }

        @Override
        public Node<K,V> getChild(int ind) {
            if (ind == -1) return this.leftChild;
            else return this.getEntry(ind).rightChild();
        }

        @Override
        public int searchAndSplit(Comparable<? super K> needle) {
            // do a regular search, and return index if found
            int sind = this.search(needle);
            if (sind >= 0) return sind;

            // lookup child and return search index if it is not full
            int cind = -sind - 2;
            Node<K,V> curChild = getChild(cind);
            if (!curChild.isFull()) return sind;

            // split the full child, then compare with promoted key to get final result
            this.insert(cind+1, curChild.split());
            int cmp = needle.compareTo(this.getKey(cind+1));
            if (cmp == 0) return cind+1;
            else if (cmp < 0) return sind;
            else return sind-1;
        }

        @Override
        protected InternalNode<K,V> splitRight(BTEntry<K,V> middle,
                java.util.Collection<? extends BTEntry<K,V>> after)
        { return new InternalNode<K,V>(middle.rightChild(), after); }
    }

    private Node<K,V> root = new LeafNode<K,V>();
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

    @Override
    public boolean containsKey(Object key) {
        // annoying unchecked conversion because Map specifies the key has type Object
        @SuppressWarnings("unchecked")
        Comparable<? super K> needle = (Comparable<? super K>) key;

        Node<K,V> cur = this.root;

        // null is returned by getChild in the leaf nodes
        while (cur != null) {
            int ind = cur.search(needle);
            // positive index means it was found
            if (ind >= 0) return true;

            // negative index calculation to get the child index if not found
            cur = cur.getChild(-ind - 2);
        }

        // if you get here, it means the key was not found in any node
        return false;
    }

    @Override
    public V get(Object key) {
        // annoying unchecked conversion because Map specifies the key has type Object
        @SuppressWarnings("unchecked")
        Comparable<? super K> needle = (Comparable<? super K>) key;

        Node<K,V> cur = this.root;

        // null is returned by getChild in the leaf nodes
        while (cur != null) {
            int ind = cur.search(needle);
            // positive index means it was found
            if (ind >= 0) return cur.getValue(ind);

            // negative index calculation to get the child index if not found
            cur = cur.getChild(-ind - 2);
        }

        // if you get here, it means the key was not found in any node
        return null;
    }

    @Override
    public V put(K key, V value) {
        // putting a null value is the same as removal, but removal is faster (no splitting)
        if (value == null) return this.remove(key);

        // check if we need to split the root
        if (this.root.isFull()) {
            this.root = new InternalNode<>(this.root, this.root.split());
            ++this.ht;
        }

        Node<K,V> cur = this.root;

        // not an infinite loop, because it will eventually get to a leaf node
        while (true) {
            assert ! cur.isFull();

            int ind = cur.searchAndSplit(key);
            if (ind >= 0) {
                // found it, just replace
                V old = cur.replaceValue(ind, value);
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

            cur = cur.getChild(ind - 1);
        }
    }

    // same as put(key, null) but avoids splits.
    @Override
    public V remove(Object key) {
        // annoying unchecked conversion because Map specifies the key has type Object
        @SuppressWarnings("unchecked")
        Comparable<? super K> needle = (Comparable<? super K>) key;

        Node<K,V> cur = this.root;

        while (cur != null) {
            int ind = cur.search(needle);
            if (ind >= 0) {
                V old = cur.replaceValue(ind, null);
                if (old != null) --this.sz;
                return old;
            }

            cur = cur.getChild(-ind - 2);
        }

        return null;
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
        private java.util.Deque<java.util.Iterator<BTEntry<K,V>>> stack;
        // prev is the entry that was just returned, next is the one that will be returned.
        private BTEntry<K,V> prev = null;
        private BTEntry<K,V> next = null;

        /** Creates a new instance attached to the containin BTree class. */
        public Iter() {
            stack = new java.util.ArrayDeque<>(BTree.this.height());

            if (BTree.this.isEmpty()) return;

            Node<K,V> cur = BTree.this.root;
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
                Node<K,V> child = next.rightChild();

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
