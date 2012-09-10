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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drpowell.tabix.Tabix.Chunk;
import org.drpowell.tabix.Tabix.ReferenceBinIndex;

import net.sf.samtools.util.BlockCompressedInputStream;

public class TabixReader
{
	public final String filename;
	private BlockCompressedInputStream mFp;
	// private static Logger logger = Logger.getLogger(TabixReader.class.getCanonicalName());

	public final Tabix tabix;
	private ArrayList<String> headers;

	private static boolean less64(final long u, final long v) { // unsigned 64-bit comparison
		return (u < v) ^ (u < 0) ^ (v < 0);
	}
	
	private Tabix readHeader(BlockCompressedInputStream bcis) throws IOException {
		byte[] buf = new byte[4];

		bcis.read(buf, 0, 4); // read "TBI\1"
		String [] mSeq = new String[readInt(bcis)]; // # sequences
		int mPreset = readInt(bcis);
		int sequenceColumn = readInt(bcis);
		int beginColumn = readInt(bcis);
		int endColumn = readInt(bcis);
		int metaCharacter = readInt(bcis);
		int linesToSkip = readInt(bcis);
		
		Tabix t = new Tabix(mPreset, sequenceColumn, beginColumn, endColumn, (char) metaCharacter, linesToSkip);

		// read sequence dictionary
		int i, j, k, l = readInt(bcis);
		buf = new byte[l];
		bcis.read(buf);
		for (i = j = k = 0; i < buf.length; ++i) {
			if (buf[i] == 0) {
				byte[] b = new byte[i - j];
				System.arraycopy(buf, j, b, 0, b.length);
				String s = new String(b);
				t.mChr2tid.put(s, k);
				mSeq[k++] = s;
				j = i + 1;
			}
		}

		// read the index
		for (i = 0; i < mSeq.length; ++i) {
			// the binning index
			int n_bin = readInt(bcis);
			Tabix.ReferenceBinIndex binMap = new Tabix.ReferenceBinIndex(1 + n_bin * 4 / 3, 0.75f);
			t.binningIndex.add(binMap);
			for (j = 0; j < n_bin; ++j) {
				int bin = readInt(bcis);
				int n_chunks = readInt(bcis);
				ArrayList<Tabix.Chunk> chunks = new ArrayList<Tabix.Chunk>(n_chunks);
				for (k = 0; k < n_chunks; ++k) {
					long u = readLong(bcis);
					long v = readLong(bcis);
					chunks.add(new Tabix.Chunk(u, v)); // in C, this is inefficient
				}
				binMap.put(bin, chunks);
			}
			// the linear index
			int n_linear = readInt(bcis);
			Tabix.ReferenceLinearIndex linear = new Tabix.ReferenceLinearIndex(n_linear);
			for (k = 0; k < n_linear; ++k)
				linear.add(readLong(bcis));
			t.linearIndex.add(linear);
		}
		
		return t;
	}
	
	/**
	 * The constructor which will use the default index (basename + ".tbi")
	 *
	 * @param filename File name of the data file
	 */
	public TabixReader(final String filename) throws IOException {
		this(filename, filename+".tbi");
	}
	
	/**
	 * The constructor where a separate index file is specified.
	 * 
	 * @param filename
	 * @param indexFileName
	 * @throws IOException
	 */
	public TabixReader(final String filename, final String indexFileName) throws IOException {
		this.filename = filename;
		mFp = new BlockCompressedInputStream(new File(filename));
		BlockCompressedInputStream is = new BlockCompressedInputStream(new File(filename + ".tbi"));
		tabix = readHeader(is);
		is.close();
	}

	public static int readInt(final InputStream is) throws IOException {
		byte[] buf = new byte[4];
		is.read(buf);
		return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	public static long readLong(final InputStream is) throws IOException {
		byte[] buf = new byte[8];
		is.read(buf);
		return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}

	/**
	 * Parse a region in the format of "chr1", "chr1:100" or "chr1:100-1000"
	 *
	 * @param reg Region string
	 * @return An array where the three elements are sequence_id,
	 *         region_begin and region_end. On failure, sequence_id==-1.
	 */
	public int[] parseReg(final String reg) { // FIXME: NOT working when the sequence name contains : or -.
		String chr;
		int colon, hyphen;
		int[] ret = new int[3];
		colon = reg.lastIndexOf(':'); hyphen = reg.lastIndexOf('-');
		chr = colon >= 0? reg.substring(0, colon) : reg;
		ret[1] = colon >= 0? Integer.parseInt(reg.substring(colon+1, hyphen >= 0? hyphen : reg.length())) - 1 : 0;
		ret[2] = hyphen >= 0? Integer.parseInt(reg.substring(hyphen+1)) : 0x7fffffff;
		Integer tid = tabix.getIdForChromosome(chr);
		ret[0] = tid == null? -1 : tid.intValue();
		return ret;
	}

	public List<String> readHeaders() throws IOException {
		if (headers == null) {
			ArrayList<String> tmpHeaders = new ArrayList<String>(tabix.linesToSkip);
			int skiplinesRemaining = tabix.linesToSkip;
			mFp.seek(0);
			String line;
			while ((line = mFp.readLine()) != null) {
				skiplinesRemaining--;
				if (skiplinesRemaining >= 0 || line.charAt(0) == tabix.commentChar) {
					tmpHeaders.add(line);
				} else {
					break;
				}
			}
			headers = tmpHeaders;
		}
		return Collections.unmodifiableList(headers);
	}
	
	public class Iterator {
		private int i;
		// private int n_seeks;
		private int tid, beg, end;
		private Tabix.Chunk[] off;
		private long curr_off;
		private boolean iseof;

		public Iterator(final int _tid, final int _beg, final int _end, final Tabix.Chunk[] _off) {
			i = -1; curr_off = 0; iseof = false;
			// n_seeks = 0;
			off = _off; tid = _tid; beg = _beg; end = _end;
		}

		public String [] next() throws IOException {
			if (iseof) return null;
			for (;;) {
				if (curr_off == 0 || !less64(curr_off, off[i].v)) { // then jump to the next chunk
					if (i == off.length - 1) break; // no more chunks
					if (i >= 0) assert(curr_off == off[i].v); // otherwise bug
					if (i < 0 || off[i].v != off[i+1].u) { // not adjacent chunks; then seek
						mFp.seek(off[i+1].u);
						curr_off = mFp.getFilePointer();
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
				if ((s = mFp.readLine()) != null) {
					curr_off = mFp.getFilePointer();
					if (s.length() == 0 || s.startsWith(tabix.comment)) continue;
					String [] row = s.split("\t");
					Tabix.GenomicInterval intv;
					try {
						intv = tabix.getInterval(row);
					} catch (NumberFormatException nfe) {
						nfe.printStackTrace();
						System.err.println("Skipping...");
						continue;
					}
					if (intv.tid != tid || intv.beg >= end) break; // no need to proceed
					else if (intv.end > beg && intv.beg < end) return row; // overlap; return
				} else break; // end of file
			}
			iseof = true;
			return null;
		}
	};

	public Iterator query(final int tid, final int beg, final int end) {
		Tabix.Chunk[] off;
		List<Tabix.Chunk> chunks;
		long min_off;
		// Tabix.Index idx = mIndex[tid];
		List<Long> linear = tabix.linearIndex.get(tid);
		ReferenceBinIndex binning = tabix.binningIndex.get(tid);
		List<Integer> bins = Tabix.reg2bins(beg, end);
		int i, l, n_off;
		if (!linear.isEmpty())
			min_off = (beg>>Tabix.TAD_LIDX_SHIFT >= linear.size())? 
					linear.get(linear.size() - 1) : linear.get(beg>>Tabix.TAD_LIDX_SHIFT);
		else min_off = 0;
		ArrayList<Tabix.Chunk> offList = new ArrayList<Tabix.Chunk>();
		for (Integer bin : bins) {
			if ((chunks = binning.get(bin)) != null ) {
				for (Tabix.Chunk chunk : chunks) {
					if (less64(min_off, chunk.v)) offList.add(chunk);
				}
			}
		}
		if (offList.isEmpty()) return new Iterator(tid, beg, end, new Tabix.Chunk[0]);
		n_off = offList.size();
		off = (Tabix.Chunk []) offList.toArray(new Tabix.Chunk [n_off]);

		// resolve completely contained adjacent blocks
		for (i = 1, l = 0; i < n_off; ++i) {
			if (less64(off[l].v, off[i].v)) {
				++l;
				off[l] = off[i];
			}
		}
		n_off = l + 1;
		// resolve overlaps between adjacent blocks; this may happen due to the merge in indexing
		for (i = 1; i < n_off; ++i)
			if (!less64(off[i-1].v, off[i].u)) off[i-1] = new Tabix.Chunk(off[i-1].u, off[i].u);
		// merge adjacent blocks
		for (i = 1, l = 0; i < n_off; ++i) {
			if (off[l].v>>16 == off[i].u>>16) off[l] = new Tabix.Chunk(off[l].u, off[i].v);
			else {
				++l;
				off[l] = off[i];
			}
		}
		n_off = l + 1;
		// return
		Tabix.Chunk[] ret = Arrays.copyOf(off, n_off);
		if (ret.length == 1 && ret[0] == null) ret = new Tabix.Chunk[0]; // not sure how this would happen
		return new TabixReader.Iterator(tid, beg, end, ret);
	}
	
	public Iterator query(final String reg) {
		int[] x = parseReg(reg);
		return query(x[0], x[1], x[2]);
	}
	
	public static String join(String [] strings, String delimiter) {
		// the most-rewritten function in the java language
		if (strings.length == 0) return "";
		StringBuilder sb = new StringBuilder(strings[0]);
		for (int i = 1; i < strings.length; i++) {
			sb.append(delimiter).append(strings[i]);
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: java -cp .:sam.jar TabixReader <in.gz> [region]");
			System.exit(1);
		}
		try {
			TabixReader tr = new TabixReader(args[0]);
//			String s;
			if (args.length != 1) { 
				// no region is specified; print the whole file
//				while ((s = tr.readLine()) != null)
//					System.out.println(s);
//			} else { // a region is specified; random access
				String [] row;
				TabixReader.Iterator iter = tr.query(args[1]); // get the iterator
				while (iter != null && (row = iter.next()) != null)
					System.out.println(join(row, "\t"));
			}
		} catch (IOException e) {
		}
	}

	public Integer getIdForChromosome(String chromosome) {
		return tabix.getIdForChromosome(chromosome);
	}
}
