import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ExpiringMap<K, V> implements Map<K, V> {

	/**
	 * 
	 * @author Asim
	 * EntryHashMap class extends the existing HashMap class
	 * It is used to internally store the Keys added to the ExpiringMap.
	 * It uses a SortedSet to keep the entries in sorted order 
	 * according to how much time has been elapsed since they were entered.
	 * 
	 * @param <K>
	 * @param <V>
	 */

	private static class EntryHashMap<K, V> extends HashMap<K, EntryNode<K, V>> {

		private static final long serialVersionUID = 1L;

		/** A SortedSet to keep the Entries in a sorted order for fast eviction */
		SortedSet<EntryNode<K, V>> sortedSet = new TreeSet<EntryNode<K, V>>();

		@Override
		public void clear() {
			super.clear();
			sortedSet.clear();
		}

		@Override 
		public boolean containsValue(Object value) {
			for(EntryNode<K, V> entry : values()) {
				V val = entry.getValue();
				if(val != null && value.equals(val)){
					return true;
				}
			}
			return false;
		}

		public EntryNode<K, V> getEntireEntry(Object value) {
			for(EntryNode<K, V> entry : values()) {
				V val = entry.getValue();
				if(val != null && value.equals(val)){
					return entry;
				}
			}
			return null;
		}

		@Override 
		public EntryNode<K, V> put(K key, EntryNode<K, V> value) {
			sortedSet.add(value);
			return super.put(key, value);
		}

		@Override
		public EntryNode<K, V> remove(Object key) {
			EntryNode<K, V> node = super.remove(key);
			if(node != null) sortedSet.remove(node);
			return node;
		}

		/** 
		 * This abstract class provides a blueprint for an Iterator Object 
		 * that can be extended by any subclasses to Iterate over both Keys
		 * and Values
		 */
		abstract class AbstractIterator {

			private Iterator<EntryNode<K, V>> iterator = sortedSet.iterator();

			/** Implementation of AbstractIterator keeps track of the next element in the sorted set after retrieving an item */
			protected EntryNode<K, V> next;

			public boolean hasNext() {
				return iterator.hasNext();
			}

			public EntryNode<K, V> getNext() {
				next = iterator.next();
				return next;
			}

		}

		final class ValueIterator extends AbstractIterator implements Iterator<V> {

			@Override
			public final V next() {
				return getNext().value;
			}

		}

		final class KeyIterator extends AbstractIterator implements Iterator<K> {
			public final K next() {
				return getNext().key;
			}
		}

		final class EntryNodeIterator extends AbstractIterator implements Iterator<Map.Entry<K, V>> {

			public final Map.Entry<K, V> next() {
				EntryNode<K, V> entry = getNext();
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

	/**
	 * This class provides the blueprints for the EntryNode Objects
	 * These objects contain the entries as a bundle of Key and Values
	 * entered into the ExpiringMap. The keys in the internalMap map
	 * to these EntryNodes. They also keep track of the time that 
	 * an entry was accessed last.
	 * 
	 * @author Asim
	 * 
	 * @param <K>
	 * @param <V>
	 */
	static class EntryNode<K, V> implements Comparable<EntryNode<K, V>> {

		/** Time this entry was last accessed */
		long lastAccessedTime;
		K key;
		V value;

		/** Constructor for the new EntryNode Object 
		 * 
		 * @param key 
		 * @param value
		 * @param last time the entry was accessed
		 */
		public EntryNode(K key, V value){
			this.key = key;
			this.value = value;
			this.lastAccessedTime = System.currentTimeMillis();
		}

		public EntryNode() {
			this.lastAccessedTime = Long.MIN_VALUE;
		}

		public synchronized long getLastAccessedTime() {
			return this.lastAccessedTime;
		}

		/** Changes the time this entry was last accessed */
		public synchronized void setLastAccessedTime(long newTime) {
			this.lastAccessedTime = newTime;
		}

		/** Gets the value for this entry */
		public synchronized V getValue() {
			return value;
		}

		/** Set the value for this entry */
		public synchronized void setValue(V value) {
			this.value = value;
		}

		/** Get the key for this entry 
		 * @return */
		public synchronized K getKey() {
			return this.key;
		}

		@Override
		public int compareTo(EntryNode<K, V> o) {
			if(key.equals(o.key)) return 0;
			if(lastAccessedTime < o.lastAccessedTime) {
				return -1;
			} else {
				return 1;
			}
		}

		@Override
		public boolean equals(Object obj) {

			if(this == obj) {
				return true;
			} else if (obj == null) {
				return false;
			} else if (this.getClass() != obj.getClass()){
				return false;
			}

			EntryNode<?, ?> other = (EntryNode<?, ?>) obj;
			if(!this.key.equals(other.getKey())) {
				return false;
			} else if (this.value == null) {
				if(other.getValue() != null) return false;
			} else if (!this.value.equals(other.getValue())) {
				return false;
			}
			return true;

		}

		@Override
		public int hashCode() {
			int bigPrime = 51;
			int hash = 1;
			hash = bigPrime * hash + ((key == null) ? 0 : key.hashCode());
			hash = bigPrime * hash + ((value == null) ? 0 : value.hashCode());
			return hash;
		}

		@Override
		public String toString() {
			return value.toString();
		}

	}


	/** Instantiating the locks that will be used by the methods in ExpiringMap class */
	private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private final Lock readLock = readWriteLock.readLock();
	private final Lock writeLock = readWriteLock.writeLock();

	/** Time To Live for each each Entry as specified by the User */
	private long myTimeToLiveInMillis;

	/** The internal EntryHashMap that keeps track of all the entries contained in the ExpiringMap so far*/
	EntryHashMap<K, V> myInternalMap;

	/** */
	private Thread evictorThread = null;

	/**
	 * Constructor for Expiring Map which creates a new Evictor thread 
	 * @param timeToLiveInMillis
	 * @throws NullPointerException
	 */

	private class Evictor extends Thread {

		private long timeToLive;

		private final Logger LOGGER = LoggerFactory.getLogger(Evictor.class);

		Evictor(long ttl){
			this.timeToLive = ttl;
		}

		@Override
		public void run() {
			boolean run = true;
			while(run) {
				try {
					LOGGER.debug("Evictor sleeping...");
					Thread.sleep(timeToLive);
					evictEntries();
					LOGGER.debug("Awake and Evicting!");
				} catch (InterruptedException e) {
					LOGGER.error("Exception", e);
					run = false;
				}
			}
		}
	}


	public ExpiringMap(long timeToLiveInMillis) throws NullPointerException {

		if(timeToLiveInMillis < 0){
			throw new IllegalArgumentException("Please provide a positive time value");
		}

		this.myTimeToLiveInMillis = timeToLiveInMillis;
		this.myInternalMap = new EntryHashMap<K, V>();
		/** Run a new thread to clean up the entries which have expired */
		if(this.myTimeToLiveInMillis > 0) {

			intitialize();

			/*
			this.timer = new Timer();
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					evictEntries();
				}
			}, this.myTimeToLiveInMillis, this.myTimeToLiveInMillis);
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
			 */
		}
	}

	/**
	 * This method initializes the Evictor Thread
	 */
	private void intitialize() {
		new Evictor(this.myTimeToLiveInMillis).start();
	}

	/**
	 * This functions evicts the expired entries from the 
	 * internal map. It is called at regular intervals 
	 * by the evictor thread as specified by the 
	 * time to live in milliseconds provided by the User.
	 */
	private void evictEntries() {
		if(myInternalMap.size() == 0){
			return;
		}
		writeLock.lock();
		try {
			List<K> keys = new ArrayList<K>();
			for(EntryNode<K, V> node : myInternalMap.sortedSet) {
				if(System.currentTimeMillis() - node.lastAccessedTime > this.myTimeToLiveInMillis) {
					keys.add(node.getKey());
				}
			}
			for(K key : keys){
				remove(key);
			}
		} finally {
			writeLock.unlock();
		}

	}

	/**
	 * Returns the current size of the Expiring Map
	 */
	@Override
	public int size() {
		readLock.lock();
		try {
			return this.myInternalMap.size();
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Returns whether the Expiring Map has any entries
	 */
	@Override
	public boolean isEmpty() {
		readLock.lock();
		try {
			return this.myInternalMap.isEmpty();
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Returns true if the Expiring Map contains the key 
	 * and updates the entry's last access time
	 */
	@Override
	public boolean containsKey(Object key) {
		readLock.lock();
		try {
			EntryNode<K, V> node = myInternalMap.get(key);
			/** Update the last time accessed for this entry if found*/
			if(node != null) {
				handleNewAccess(node);
				return true;
			}
			return false;
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Returns a true if the ExpiringMap contains the specified value
	 * and updates the entry's last access time 
	 */
	@Override
	public boolean containsValue(Object value) {
		readLock.lock();
		try {
			EntryNode<K, V> node = myInternalMap.getEntireEntry(value);
			/** Update the last time accessed for this entry if found*/
			if(node != null) {
				handleNewAccess(node);
				return true;
			}
			return false;
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Returns the Value corresponding to the Key specified
	 * Also updates the last access time for an entry
	 */
	@Override
	public V get(Object key) {
		readLock.lock();
		try {
			EntryNode<K, V> node = getNode(key);
			if(node != null) {
				handleNewAccess(node);
			}
			return node == null ? null : node.getValue();
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Returns the EntryNode corresponding to the Key specified
	 * @param key
	 * @return
	 */
	private EntryNode<K, V> getNode(Object key) {
		readLock.lock();
		try {
			return myInternalMap.get(key);
		} finally {
			readLock.unlock();
		}
	}

	@SuppressWarnings("unused")
	private EntryNode<K, V> removeNode(Object key) {
		writeLock.lock();
		try {
			return myInternalMap.remove(key);
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Updates the last time accessed for an entry
	 * @param node
	 */
	private void handleNewAccess(EntryNode<K, V> node) {
		node.setLastAccessedTime(System.currentTimeMillis());
	}

	@Override
	public V put(K key, V value) {
		return putInMap(key, value);
	}

	/**
	 * This function puts the Key and EntryNode into
	 * the internal map. It also checks if the 
	 * internal map already contains the key, and 
	 * then updates the corresponding value accordingly
	 * @param key
	 * @param value
	 * @return
	 */
	private V putInMap(K key, V value) {
		writeLock.lock();
		try {
			EntryNode<K, V> node = getNode(key);
			/** Create a new Node if it's a new key */
			if(node == null) {
				node = new EntryNode<K, V>(key, value);
				myInternalMap.put(key, node);
			} else {
				/** Update the value and last access time for the key */
				node.setValue(value);
				handleNewAccess(node);
			}
			return node.getValue();
		} finally {
			writeLock.unlock();
		}

	}

	@Override
	public V remove(Object key) {
		writeLock.lock();
		try {
			V value = null;
			EntryNode<K, V> nodeToRemove = this.myInternalMap.remove(key);
			if(nodeToRemove == null){
				return value;
			} else {
				value = nodeToRemove.getValue();
				//System.out.println("Removing Object: " + nodeToRemove.getKey() + " " + nodeToRemove.getValue());
				return value;
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		writeLock.lock();
		try {
			for(java.util.Map.Entry<? extends K, ? extends V> entry : m.entrySet()){
				put(entry.getKey(), entry.getValue());
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public void clear() {
		writeLock.lock();
		try {
			myInternalMap.clear();
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public Set<K> keySet() {
		readLock.lock();
		try {
			return myInternalMap.keySet();
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public Collection<V> values() {
		return new AbstractCollection<V>() {
			@Override
			public int size() {
				return myInternalMap.size();
			}
			@Override
			public void clear() {
				ExpiringMap.this.clear();
			}
			@Override
			public Iterator<V> iterator() {
				return (myInternalMap).new ValueIterator();
			}
		};
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return new AbstractSet<Map.Entry<K, V>>() {

			@Override
			public Iterator<Map.Entry<K, V>> iterator() {
				return (myInternalMap).new EntryNodeIterator();					
			}

			@Override
			public int size() {
				return ExpiringMap.this.size();
			}

		};
	}

	@Override 
	public String toString() {
		readLock.lock();
		try {
			return this.myInternalMap.toString();
		} finally {
			readLock.unlock();
		}
	}

	public void printMap() {
		if(this.myInternalMap.size() == 0) System.out.println("Map empty!");
		System.out.println("Printing Map.......");
		for(Map.Entry<K, V> entry : this.entrySet()) {
			System.out.println("["+entry.getKey()+":"+entry.getValue()+"]");
		}
	}

}