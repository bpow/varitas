package org.drpowell.tabix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drpowell.tabix.TabixIndex.Chunk;

/**
 * Maps integers (bin numbers) to a list of chunks that overlap
 * @author bpow
 *
 */
class BinIndex {
	private Map<Integer, List<Chunk>> map;
	public BinIndex(int capacity, float loadFactor) {
		map = new HashMap<Integer, List<Chunk>>(capacity, loadFactor);
	}
	public BinIndex() {
		this(TabixIndex.TBX_MAX_BIN, 1.0f);
	}
	public BinIndex(BinIndex other) {
		// a new clone with size sufficient to hold the contents of "other"
		map = new HashMap<Integer, List<Chunk>>(other.map);
		
	}
	public List<Chunk> getWithNew(int i) {
		List<Chunk> out = get(i);
		if (out == null) {
			out = new ArrayList<Chunk>(); // TODO- presize or change to LinkedList
		}
		put(i, out);
		return out;
	}
	void put(int i, List<Chunk> chunks) {
		map.put(i, chunks);
	}
	List<Chunk> get(int i) {
		return map.get(i);
	}
	public int size() {
		return map.size();
	}
	public Collection<Integer> bins() {
		return map.keySet();
	}
	public boolean isEmpty() {
		return map.isEmpty();
	}
	
}