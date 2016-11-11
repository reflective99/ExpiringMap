import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * An expiring map is a Map in which entries 
 * are evicted of the map after their time to live
 * expired.
 * 
 * If an map entry hasn't been accessed for <code>
 * timeToLiveMillis</code> the map entry is evicted
 * out of the map, subsequent to which an attempt
 * to get the key from the map will return null. 
 * 
 * @param <K>
 * @param <V>
 */
public class ExpiringMap2<K, V> implements Map<K, V> {

	private static class DNodeLinkedHashMap<K, V> extends HashMap<K, DLinkListNode<K, V>> {

		private static final long serialVersionUID = 1L;

		SortedSet<DLinkListNode<K, V>> sortedSet = new TreeSet<DLinkListNode<K, V>>();

		@Override
		public void clear() {
			super.clear();
			sortedSet.clear();
		}

		public Iterator<DLinkListNode<K, V>> valuesIterator() {
			return new DLinkListNodeIterator();
		}

		@Override 
		public boolean containsValue(Object value) {
			for(DLinkListNode<K, V> entry : values()) {
				V val = entry.value;
				if(val != null && value.equals(val)){
					return true;
				}
			}
			return false;
		}

		public DLinkListNode<K, V> first() {
			return sortedSet.isEmpty() ? null : sortedSet.first();
		}

		@Override 
		public DLinkListNode<K, V> put(K key, DLinkListNode<K, V> value) {
			sortedSet.add(value);
			return super.put(key, value);
		}

		@Override
		public DLinkListNode<K, V> remove(Object key) {
			DLinkListNode<K, V> node = super.remove(key);
			if(node != null) sortedSet.remove(node);
			return node;
		}

		abstract class AbstractIterator {
			private Iterator<DLinkListNode<K, V>> iterator = sortedSet.iterator();
			protected DLinkListNode<K, V> next;

			public boolean hasNext() {
				return iterator.hasNext();
			}

			public DLinkListNode<K, V> getNext() {
				next = iterator.next();
				return next;
			}

			public void remove() {
				DNodeLinkedHashMap.super.remove(next.key);
				iterator.remove();
			}

		}

		final class DLinkListNodeIterator extends AbstractIterator implements Iterator<DLinkListNode<K, V>> {

			@Override
			public DLinkListNode<K, V> next() {
				return getNext();
			}

		}
		
		final class ValueIterator extends AbstractIterator implements Iterator<V> {

			@Override
			public V next() {
				return getNext().value;
			}
			
		}

		final class NodeEntryIterator extends AbstractIterator implements Iterator<Map.Entry<K, V>> {
			public final Map.Entry<K, V> next() {
				DLinkListNode<K, V> entry = getNext();
				return new Map.Entry<K, V>() {
					@Override
					public K getKey() {
						return entry.key;
					}

					@Override
					public V getValue() {
						return entry.value;
					}

					@Override
					public V setValue(V value) {
						throw new UnsupportedOperationException();
					}
				};

			}
		}



	}

	private long myTimeToLiveInMillis;
	DNodeLinkedHashMap<K, V> map;
	private DLinkListNode<K, V> head;
	private DLinkListNode<K, V> tail;

	public ExpiringMap2(long timeToLiveInMillis) throws NullPointerException {

		if(timeToLiveInMillis < 0){
			throw new IllegalArgumentException("Please provide a positive time value");
		}

		this.myTimeToLiveInMillis = timeToLiveInMillis;
		this.head = new DLinkListNode<K, V>();
		this.head.setLastAccessedTime(Long.MAX_VALUE);
		this.tail = new DLinkListNode<K, V>();
		this.tail.setLastAccessedTime(Long.MAX_VALUE);
		head.setNext(tail);
		head.setPrev(null);
		tail.setNext(null);
		tail.setPrev(head);
		this.map = new DNodeLinkedHashMap<K, V>();
		// Run a new thread to clean up the entries which have expired
		if(this.myTimeToLiveInMillis > 0) {
			Thread evictor = new Thread(new Runnable() {
				@Override
				public void run() {
					while(true) {
						try {
							Thread.sleep(myTimeToLiveInMillis);
						} catch (InterruptedException e) {

						}
						doCleanUp();
					}
				}

			});
			evictor.setDaemon(true);
			evictor.start();
		}
	}

	private synchronized void doCleanUp() {

		if(map.size() == 0){
			return;
		}

		DLinkListNode<K, V> curr = tail.getPrev();

		while(System.currentTimeMillis() - curr.getLastAccessedTime() > myTimeToLiveInMillis) {
			remove(curr.getKey());
			curr = curr.getPrev();
		}

		return;
	}

	@Override
	public int size() {
		synchronized(this.map) {
			return this.map.size();
		}
	}

	@Override
	public boolean isEmpty() {
		synchronized(this.map) {
			return this.map.isEmpty();
		}
	}

	@Override
	public boolean containsKey(Object key) {
		synchronized(this.map) {
			return this.map.containsKey(key);
		}
	}

	@Override
	public boolean containsValue(Object value) {
		return false;
	}

	@Override
	public V get(Object key) {
		synchronized(this.map) {
			DLinkListNode<K, V> node = this.map.get(key);
			if(node != null) {
				node = handleNewAccess(node);
				if(node == null) this.map.remove(key);
				return node == null ? null : node.getValue();
			}
			return null;
		}

	}

	private DLinkListNode<K, V> handleNewAccess(DLinkListNode<K, V> node) {
		long timeElapsed = node.getLastAccessedTime();
		long currentTime = System.currentTimeMillis();
		if(currentTime - timeElapsed > this.myTimeToLiveInMillis){
			removeNode(node);
			return null;
		} 
		removeNode(node);
		addToFront(node);
		node.setLastAccessedTime(System.currentTimeMillis());
		return node;
	}

	private void addToFront(DLinkListNode<K, V> node) {
		System.out.println();
		DLinkListNode<K, V> post = this.head.getNext();
		node.setNext(post);
		this.head.setNext(node);
		node.setPrev(head);
		post.setPrev(node);
	}

	private void removeNode(DLinkListNode<K, V> node) {
		if(node == null) return;
		DLinkListNode<K, V> prev = node.getPrev();
		DLinkListNode<K, V> next = node.getNext();
		prev.setNext(next);
		next.setPrev(prev);
	}

	@Override
	public V put(K key, V value) {
			return putInMap(key, value);
	}

	private synchronized V putInMap(K key, V value) {
		DLinkListNode<K, V> entry = map.get(key);
		
		if(entry == null) {
			entry = new DLinkListNode<K, V>(key, value);
			map.put(key, entry);
		} else {
			entry.setValue(value);
			entry.setLastAccessedTime(System.currentTimeMillis());
		}
		return entry.getValue();
	}

	@Override
	public V remove(Object key) {
		synchronized(this.map) {
			V value = null;
			if(this.map.containsKey(key)) {
				DLinkListNode<K, V> nodeToRemove = this.map.remove(key);
				value = nodeToRemove.getValue();
				System.out.println("Removing Object: " + nodeToRemove.getKey() + " " + nodeToRemove.getValue());
				return value;
			} else {
				return value;
			}
		}
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		synchronized(this.map) {

		}
	}

	@Override
	public void clear() {
		synchronized(this.map) {
			this.head.setNext(this.tail);
			this.tail.setPrev(this.head);
			this.map.clear();
		}
	}

	@Override
	public Set<K> keySet() {
		synchronized(this.map) {
			return this.map.keySet();
		}
	}

	@Override
	public synchronized Collection<V> values() {
		return new AbstractCollection<V>() {

			@Override
			public Iterator<V> iterator() {
				return (map).new ValueIterator();
			}

			@Override
			public int size() {
				return ExpiringMap2.this.size();
			}
			
		};
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		synchronized (this.map) {
			return new AbstractSet<Map.Entry<K, V>>() {

				@Override
				public Iterator<Map.Entry<K, V>> iterator() {
					return (map).new NodeEntryIterator();					
				}

				@Override
				public int size() {
					return ExpiringMap2.this.size();
				}

			};
		}
	}

	static class DLinkListNode<K, V> implements Comparable<DLinkListNode<K, V>> {
		DLinkListNode<K, V> next;
		DLinkListNode<K, V> prev;
		/** Time this entry was last accessed */
		long lastAccessedTime;
		K key;
		V value;

		/** Constructor for the new ExpiringEntry Object 
		 * 
		 * @param key 
		 * @param value
		 * @param last time the entry was accessed
		 */
		public DLinkListNode(K key, V value){
			this.key = key;
			this.value = value;
			this.lastAccessedTime = System.currentTimeMillis();
		}

		/** 
		 * @return
		 */
		public DLinkListNode() {
			this.lastAccessedTime = Long.MIN_VALUE;
		}

		public long getLastAccessedTime() {
			return this.lastAccessedTime;
		}

		/** Changes the time this entry was last accessed */
		public void setLastAccessedTime(long newTime) {
			this.lastAccessedTime = newTime;
		}

		public void setNext(DLinkListNode<K, V> node) {
			this.next = node;
		}

		public void setPrev(DLinkListNode<K, V> node) {
			this.prev = node;
		}

		public DLinkListNode<K, V> getPrev() {
			return this.prev;
		}

		public DLinkListNode<K, V> getNext() {
			return this.next;
		}

		/** Gets the value for this entry */
		public synchronized V getValue() {
			return value;
		}

		/** Set the value */
		public synchronized void setValue(V value) {
			this.value = value;
		}

		/** Get the key for this entry 
		 * @return */
		public synchronized K getKey() {
			return this.key;
		}

		@Override
		public int compareTo(DLinkListNode<K, V> o) {
			if(key.equals(o.key)) return 0;
			if(lastAccessedTime < o.lastAccessedTime) {
				return -1;
			} else {
				return 1;
			}
		}

		@Override
		public int hashCode() {
			final int bigPrime = 51;
			int code = 1;
			code = bigPrime * code + ((key == null) ? 0 : key.hashCode());
			code *= bigPrime + ((value == null) ? 0 : value.hashCode());
			return code;
		}

		@Override
		public String toString() {
			return value.toString();
		}

	}

	/*
	public void printDList() {
		DLinkListNode curr = head.getNext();
		while(curr != tail) {
			System.out.print("["+curr.getKey()+":"+curr.getValue()+"]->");
			curr = curr.getNext();
		}
		System.out.println();
	}

	public void printDListBackwards() {
		DLinkListNode curr = tail.getPrev();
		while(curr != head) {
			System.out.print("<-["+curr.getKey()+":"+curr.getValue()+"]");
			curr = curr.getPrev();
		}
		System.out.println();
	}

	public void printMap() {
		if(this.map.size() == 0) System.out.println("Map empty!");
		System.out.println("Printing Map.......");
		for(K key : this.map.keySet()) {
			System.out.println("["+key+":"+this.map.get(key).getValue()+"]");
		}
	}

	public void cleanUp() {
		doCleanUp();
	}
	 */

}