/* The MIT License

   Copyright (c) 2010 Broad Institute.
   Portions Copyright (c) 2012 Baylor College of Medicine.

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.samtools.util.BlockCompressedInputStream;

import org.drpowell.tabix.TabixIndex.Chunk;

/**
 * An Iterator implementation for tabix-indexed files.
 * 
 * Much of the trickier code in the advance() method is from Heng Li's original TabixReader.java 
 * @author bpow
 *
 */
public class TabixIterator implements Iterator<String []>{
	private int i;
	// private int n_seeks;
	private int tid, beg, end;
	private long curr_off;
	private boolean iseof;
	private final TabixIndex tabix;
	private final GenomicInterval intv;
	private String [] next = null;
	private List<Chunk> candidateChunks;
	private final BlockCompressedInputStream indexedStream;

	private static Logger logger = Logger.getLogger(TabixIterator.class.getCanonicalName());

	public TabixIterator(final TabixIndex index, String region) {
		this(index, index.parseInterval(region));
	}
	
	public TabixIterator(final TabixIndex index, final GenomicInterval interval) {
		this.tabix = index; this.intv = interval;
		i = -1; curr_off = 0; iseof = false;
		// n_seeks = 0;
		
		BlockCompressedInputStream bcis = null;
		candidateChunks = getCandidateChunks();
		try {
			bcis = index.getIndexedStream();
			next = advance();
		} catch (IOException e) {
			logger.log(Level.WARNING, String.format(
					"Unable to read from file '%s', so an empty result is returned for this query\n%s",
					index.clientFileName, e));
			// if an IOException was thrown, 'next' will be null, so there will be no results
		}
		indexedStream = bcis;
	}
	
	public List<Chunk> getCandidateChunks() {
		final long minimumOffset = tabix.linearIndex.get(intv.getSequenceId()).getMinimumOffset(intv.getBegin());
		ArrayList<TabixIndex.Chunk> offList = new ArrayList<TabixIndex.Chunk>();
		
		BinIndex binning = tabix.binningIndex.get(tid);
		BitSet bins = GenomicInterval.reg2bins(beg, end);
		if (bins.isEmpty()) { return offList; } // shortcut when no results
		int i, l, n_off;
		for (int bin = bins.nextSetBit(0); bin >= 0; bin = bins.nextSetBit(bin+1)) {
			List<Chunk> chunks = null;
			if ((chunks = binning.get(bin)) != null) {
				for (TabixIndex.Chunk chunk : chunks) {
					if (Chunk.cmpUInt64(minimumOffset, chunk.end) < 0) offList.add(chunk);
				}
			}
		}
		if (offList.isEmpty()) { return offList; } // shortcut when no results

		n_off = offList.size();
		Chunk [] off = (TabixIndex.Chunk []) offList.toArray(new TabixIndex.Chunk [n_off]);
		// resolve completely contained adjacent blocks
		for (i = 1, l = 0; i < n_off; ++i) {
			if (Chunk.cmpUInt64(off[l].end, off[i].end) < 0) {
				++l;
				off[l] = off[i];
			}
		}
		n_off = l + 1;
		// resolve overlaps between adjacent blocks; this may happen due to the merge in indexing
		for (i = 1; i < n_off; ++i)
			if (Chunk.cmpUInt64(off[i-1].end, off[i].begin) >= 0) off[i-1] = new TabixIndex.Chunk(off[i-1].begin, off[i].begin);
		// merge adjacent blocks
		for (i = 1, l = 0; i < n_off; ++i) {
			if (off[l].end>>16 == off[i].begin>>16) off[l] = new TabixIndex.Chunk(off[l].begin, off[i].end);
			else {
				++l;
				off[l] = off[i];
			}
		}
		n_off = l + 1;
		// return
		List<Chunk> ret = Arrays.asList(Arrays.copyOf(off, n_off));
		if (ret.size() == 1 && ret.get(0) == null) ret = new ArrayList<Chunk>(0); // not sure how this would happen
		return ret;
	}
	
	public String [] next() {
		String [] res = next;
		if (next != null) {
			next = advance();
		}
		return res;
	}
	
	private String [] advance() {
		if (iseof) return null;
		try {
		for (;;) {
			if (curr_off == 0 || Chunk.cmpUInt64(curr_off, candidateChunks.get(i).end) >= 0) { // then jump to the next chunk
				if (i == candidateChunks.size() - 1) break; // no more chunks
				if (i >= 0) assert(curr_off == candidateChunks.get(i).end); // otherwise bug
				if (i < 0 || candidateChunks.get(i).end != candidateChunks.get(i+1).begin) { // not adjacent chunks; then seek
					indexedStream.seek(candidateChunks.get(i+1).begin);
					curr_off = indexedStream.getFilePointer();
					/*
					++n_seeks;
					if (n_seeks % 100 == 0) {
						logger.warning("Seeks so far for " + TabixReader.this + ": " + n_seeks);
					}
					*/
				}
				++i;
			}
			String s;
			if ((s = indexedStream.readLine()) != null) {
				curr_off = indexedStream.getFilePointer();
				if (s.length() == 0 || s.startsWith(tabix.config.commentString)) continue;
				String [] row = s.split("\t", -1);
				GenomicInterval intv;
				try {
					intv = tabix.getInterval(row);
				} catch (NumberFormatException nfe) {
					nfe.printStackTrace();
					System.err.println("Skipping...");
					continue;
				}
				if (intv.getSequenceId() != tid || intv.getBegin() >= end) break; // no need to proceed
				else if (intv.getEnd() > beg && intv.getBegin() < end) return row; // overlap; return
			} else break; // end of file
		}
		} catch (IOException ioe) {
			exceptionHandler(ioe);
		}
		iseof = true;
		return null;
	}
	
	/**
	 * By default, when an IOException (or other Exception) occurs, this is logged and the iterator
	 * finishes. By overriding exceptionHandler, a subclass could do something else.
	 * 
	 * This is here because the Java Iterator interface does not allow for checked exceptions to be 
	 * thrown in the next() or hasNext() methods.
	 * @param e
	 */
	public void exceptionHandler(Exception e) {
		logger.log(Level.WARNING, e.toString());
	}

	@Override
	public boolean hasNext() {
		return next != null;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("TabixIterators are not mutable");
	}
}