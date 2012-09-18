/* The MIT License

   Copyright (c) 2012 Baylor College of Medicine.

   Permission is hereby granted, free of charge, to any person obtaining
   a copy of this software and associated documentation files (the
   "Software"), to deal in the Software without restriction, including
   without limitation the rights to use, copy, modify, merge, publish,
   distribute, sublicense, and/or sell copies of the Software, and to
   permit persons to whom the Software is furnished to do so, subject to
   the following conditions:

   The above copyright notice and this permission notice shall be
   included in all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
   SOFTWARE.
*/

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