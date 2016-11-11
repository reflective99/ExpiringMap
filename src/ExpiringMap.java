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
	 * EntryLinkedHashMap class extends the existing HashMap class
	 * It is used to internally store the Keys added to the ExpiringMap.
	 * It uses a SortedSet to keep the entries in sorted order 
	 * according to how much time has been elapsed since they were entered.
	 * 
	 * @param <K>
	 * @param <V>
	 */

	private static class EntryLinkedHashMap<K, V> extends HashMap<K, EntryNode<K, V>> {

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
				V val = entry.value;
				if(val != null && value.equals(val)){
					return true;
				}
			}
			return false;
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
		 * @author Asim
		 * This abstract class provides a blueprint for an Iterator Object 
		 * that can be extended by any subclasses to Iterate over both Keys
		 * and Values
		 */
		abstract class SimpleIterator {
			
			private Iterator<EntryNode<K, V>> iterator = sortedSet.iterator();
			
			/** Implementation of SimpleIterator keeps track of the next element in the sorted set after retrieving an item */
			protected EntryNode<K, V> next;

			public boolean hasNext() {
				return iterator.hasNext();
			}

			public EntryNode<K, V> getNext() {
				next = iterator.next();
				return next;
			}
			
		}

		final class ValueIterator extends SimpleIterator implements Iterator<V> {

			@Override
			public final V next() {
				return getNext().value;
			}

		}

		final class KeyIterator extends SimpleIterator implements Iterator<K> {
			public final K next() {
				return getNext().key;
			}
		}

		final class EntryNodeIterator extends SimpleIterator implements Iterator<Map.Entry<K, V>> {
			
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
	
	/** Instantiating the locks that will be used by the methods in ExpiringMap class */
	private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private final Lock readLock = readWriteLock.readLock();
	private final Lock writeLock = readWriteLock.writeLock();

	/** Time To Live for each each Entry as specified by the User */
	private long myTimeToLiveInMillis;
	
	/** The internal EntryLinkedHashMap that keeps track of all the entries contained in the ExpiringMap so far*/
	EntryLinkedHashMap<K, V> myInternalMap;
	
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
		this.myInternalMap = new EntryLinkedHashMap<K, V>();
		// Run a new thread to clean up the entries which have expired
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
	
	private void intitialize() {
		new Evictor(this.myTimeToLiveInMillis).start();
	}

	/**
	 * This function is called at regular intervals 
	 * as specified by the time to live in milliseconds
	 * It goes through all of the entries in ExpiringMap's
	 * internalMap and evicts those which have expired.
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

	@Override
	public int size() {
		synchronized(this.myInternalMap) {
			return this.myInternalMap.size();
		}
	}

	@Override
	public boolean isEmpty() {
		synchronized(this.myInternalMap) {
			return this.myInternalMap.isEmpty();
		}
	}

	@Override
	public boolean containsKey(Object key) {
		readLock.lock();
		try {
			return myInternalMap.containsKey(key);
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public boolean containsValue(Object value) {
		readLock.lock();
		try {
			return myInternalMap.containsValue(value);
		} finally {
			readLock.unlock();
		}
	}

	@SuppressWarnings("null")
	@Override
	public V get(Object key) {

		EntryNode<K, V> node = getNode(key);
		if(node != null) {
			handleNewAccess(node);
			return node == null ? null : node.getValue();
		}
		return node == null ? null : node.getValue();

	}

	private EntryNode<K, V> getNode(Object key) {
		readLock.lock();
		try {
			return myInternalMap.get(key);
		} finally {
			readLock.unlock();
		}
	}

	private EntryNode<K, V> removeNode(Object key) {
		writeLock.lock();
		try {
			return myInternalMap.remove(key);
		} finally {
			writeLock.unlock();
		}
	}

	private void handleNewAccess(EntryNode<K, V> node) {
		long timeElapsed = node.getLastAccessedTime();
		long currentTime = System.currentTimeMillis();
		if(currentTime - timeElapsed > this.myTimeToLiveInMillis){
			removeNode(node);
		} else {
			node.setLastAccessedTime(System.currentTimeMillis());
		}
	}

	@Override
	public V put(K key, V value) {
		return putInMap(key, value);
	}

	private V putInMap(K key, V value) {
		writeLock.lock();
		try {
			EntryNode<K, V> node = getNode(key);
			if(node == null) {
				node = new EntryNode<K, V>(key, value);
				myInternalMap.put(key, node);
			} else {
				node.setValue(value);
				node.setLastAccessedTime(System.currentTimeMillis());
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
	public synchronized Collection<V> values() {
		return new AbstractCollection<V>() {

			@Override
			public Iterator<V> iterator() {
				return (myInternalMap).new ValueIterator();
			}

			@Override
			public int size() {
				return ExpiringMap.this.size();
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

	static class EntryNode<K, V> implements Comparable<EntryNode<K, V>> {
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
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			EntryNode<?, ?> other = (EntryNode<?, ?>) obj;
			if (!key.equals(other.key))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int bigPrime = 51;
			int code = 1;
			code = bigPrime * code + ((key == null) ? 0 : key.hashCode());
			code = bigPrime * code + ((value == null) ? 0 : value.hashCode());
			return code;
		}

		@Override
		public String toString() {
			return value.toString();
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