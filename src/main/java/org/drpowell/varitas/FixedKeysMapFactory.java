package org.drpowell.varitas;

import java.util.Map.Entry;
import java.util.*;


/**
 * The spirit of this class is like EnumMap but with a fixed set of
 * possible keys defined at runtime. The data is for Maps produced from
 * this factory are stored in arrays for conciseness. This is best applicable 
 * when there are a number of Maps to be stored with a fixed number of keys
 * and most of the maps will have most of the keys.
 * 
 * Or it could just be a premature optimization...
 * @author bpow
 */
public class FixedKeysMapFactory<K, V> {
	private final Map<K, Integer> keyMap;
	private final List<K> keyList;

	public FixedKeysMapFactory(Iterable<K> keys) {
		int i = 0;
		HashMap<K, Integer> tmpMap = new HashMap<K, Integer>();
		ArrayList<K> tmpList = new ArrayList<K>();
		for (K k : keys) {
			tmpMap.put(k, i);
			tmpList.add(k);
			i++;
		}
		keyMap = Collections.unmodifiableMap(tmpMap);
		keyList = Collections.unmodifiableList(tmpList);
	}
	
	public Map<K, V> newMap() {
		return new FixedKeysMap(keyMap, keyList);
	}
	
	private static class FixedKeysMap<K, V> extends AbstractMap<K, V> {
		private final Map<K, Integer> keyM;
		private final List<K> keyL;
		private Object[] values;

		@Override
		public void clear() {
			values = new Object[keyM.size()];
			Arrays.fill(values, this);
		}

		@Override
		public boolean containsKey(Object key) {
			Integer i = keyM.get(key);
			if (i != null) {
				return !this.equals(values[i]);
			}
			return false;
		}

		@Override
		public boolean containsValue(Object value) {
			if (value == null) {
				for (Object v : values) {
					if (v == null) return true;
				}
			} else {
				for (Object v : values) {
					if (v.equals(value)) return true;
				}
			}
			return false;
		}

		@Override
		public V get(Object key) {
			Integer i = keyM.get(key);
			if (i == null) return null; // or could through an exception...
			Object v = values[i];
			if (v.equals(this)) return null;
			return (V) v;
		}

		@Override
		public boolean isEmpty() {
			for (Object o : values) {
				if (!this.equals(o)) return false;
			}
			return true;
		}

		@Override
		public Set keySet() {
			return super.keySet();
		}

		@Override
		public V put(K key, V value) {
			int i = indexForKey(key);
			V old = (V) values[i];
			values[i] = value;
			return checkNull(old);
		}
		
		@Override
		public V remove(Object key) {
			int i = indexForKey(key);
			V old = (V) values[i];
			values[i] = this;
			return checkNull(old);
		}

		@Override
		public int size() {
			int size = 0;
			for (Object o : values) {
				if (!this.equals(o)) size += 1;
			}
			return size;
		}

		@Override
		public Collection values() {
			return super.values();
		}
		
		private V checkNull(Object value) {
			if (this == value) return null;
			return (V) value;
		}
		
		private int indexForKey(Object key) {
			Integer i = keyM.get(key);
			if (i == null) {
				throw new IllegalArgumentException("The key " + key + " is not an acceptable key for this map");
			}
			return i;
		}
		
		protected FixedKeysMap(Map<K, Integer> keyMap, List<K> keyList) {
			this.keyM = keyMap;
			this.keyL= keyList;
			this.clear();
		}

		@Override
		public Set entrySet() {
			// TODO - could this be a singleton?
			return new FixedKeysMapEntrySet(this);
		}
		
		private Map.Entry<K, V> getEntry(int i) {
			return new FixedKeysMapEntry(this, i);
		}
		
	}
	
	private static class FixedKeysMapEntry<K, V> implements Map.Entry<K, V> {
		private final FixedKeysMap<K, V> fkm;
		private final int i;
		
		public FixedKeysMapEntry(FixedKeysMap<K, V> map, int index) {
			fkm = map;
			i = index;
		}

		@Override
		public K getKey() {
			return fkm.keyL.get(i);
		}

		@Override
		public V getValue() {
			return fkm.checkNull(fkm.values[i]);
		}

		@Override
		public V setValue(V value) {
			return fkm.put(getKey(), value);
		}
		
	}
	
	private static class FixedKeysMapSetIterator<K, V> implements Iterator<Entry<K, V>> {
		private final FixedKeysMap<K, V> fkm;
		private int index = 0;
		
		public FixedKeysMapSetIterator(FixedKeysMap<K, V> map) {
			fkm = map;
		}

		@Override
		public boolean hasNext() {
			for (int i = index; i < fkm.values.length; i++) {
				if (!fkm.equals(fkm.values[i])) return true; 
			}
			return false;
		}

		@Override
		public Entry<K, V> next() {
			for (int i = index; i < fkm.values.length; i++) {
				if (!fkm.equals(fkm.values[i])) {
					index = i + 1;
					return fkm.getEntry(i); 
				}
			}
			throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Not supported yet.");
		}
		
	}
	
	private static class FixedKeysMapEntrySet<K,V> extends AbstractSet<Map.Entry<K,V>> {
		private final FixedKeysMap<K, V> fkm;
		
		public FixedKeysMapEntrySet(FixedKeysMap<K, V> map) {
			fkm = map;
		}

		@Override
		public Iterator<Entry<K, V>> iterator() {
			return new FixedKeysMapSetIterator(fkm);
		}

		@Override
		public int size() {
			return fkm.size();
		}
		
	}
}
