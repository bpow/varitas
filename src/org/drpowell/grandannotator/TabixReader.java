/* The MIT License

   Copyright (c) 2010 Broad Institute.

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

/* Contact: Heng Li <hengli@broadinstitute.org> */

package org.drpowell.grandannotator;
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

import net.sf.samtools.util.BlockCompressedInputStream;

public class TabixReader
{
	public final String filename;
	private BlockCompressedInputStream mFp;

	private final int mPreset;
	public final int sequenceColumn;
	public final int beginColumn;
	public final int endColumn;
	public final int metaCharacter;
	public final String meta;
	public final int linesToSkip;
	private final String[] mSeq;
	private ArrayList<String> headers;

	private final HashMap<String, Integer> mChr2tid;

	private static int MAX_BIN = 37450;
	private static int TAD_MIN_CHUNK_GAP = 32768;
	private static int TAD_LIDX_SHIFT = 14;

	private class TPair64 implements Comparable<TPair64> {
		final long u;
		final long v;
		public TPair64(final long _u, final long _v) {
			u = _u; v = _v;
		}
		public int compareTo(final TPair64 p) {
			return u == p.u? 0 : ((u < p.u) ^ (u < 0) ^ (p.u < 0))? -1 : 1; // unsigned 64-bit comparison
		}
	};

	private class TIndex {
		HashMap<Integer, TPair64[]> b; // binning index
		long[] l; // linear index
	};
	private TIndex[] mIndex;

	private class TIntv {
		Integer tid;
		int beg, end;
	};

	private static boolean less64(final long u, final long v) { // unsigned 64-bit comparison
		return (u < v) ^ (u < 0) ^ (v < 0);
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
		// readIndex
		byte[] buf = new byte[4];

		is.read(buf, 0, 4); // read "TBI\1"
		mSeq = new String[readInt(is)]; // # sequences
		mChr2tid = new HashMap<String, Integer>();
		mPreset = readInt(is);
		sequenceColumn = readInt(is);
		beginColumn = readInt(is);
		endColumn = readInt(is);
		metaCharacter = readInt(is);
		meta = String.valueOf(metaCharacter);
		linesToSkip = readInt(is);
		// TODO - everything final has been assigned by now, could move the rest out of the constructor
		// read sequence dictionary
		int i, j, k, l = readInt(is);
		buf = new byte[l];
		is.read(buf);
		for (i = j = k = 0; i < buf.length; ++i) {
			if (buf[i] == 0) {
				byte[] b = new byte[i - j];
				System.arraycopy(buf, j, b, 0, b.length);
				String s = new String(b);
				mChr2tid.put(s, k);
				mSeq[k++] = s;
				j = i + 1;
			}
		}
		// read the index
		mIndex = new TIndex[mSeq.length];
		for (i = 0; i < mSeq.length; ++i) {
			// the binning index
			int n_bin = readInt(is);
			mIndex[i] = new TIndex();
			mIndex[i].b = new HashMap<Integer, TPair64[]>();
			for (j = 0; j < n_bin; ++j) {
				int bin = readInt(is);
				TPair64[] chunks = new TPair64[readInt(is)];
				for (k = 0; k < chunks.length; ++k) {
					long u = readLong(is);
					long v = readLong(is);
					chunks[k] = new TPair64(u, v); // in C, this is inefficient
				}
				mIndex[i].b.put(bin, chunks);
			}
			// the linear index
			mIndex[i].l = new long[readInt(is)];
			for (k = 0; k < mIndex[i].l.length; ++k)
				mIndex[i].l[k] = readLong(is);
		}
		// close
		is.close();
	}

	/**
	 * Calculates the bins that overlap a given region.
	 * 
	 * Although this is an implementation detail, it may be more useful in general since the
	 * same binning index is used elsewhere (in bam files, for instance). Heng Li's reg2bin had
	 * used an int[37450] which was allocated for each query. While this results in more object
	 * allocations (for the ArrayList and Integer objects), it actually works faster (with my
	 * testing) for the common case where there are not many overlapped regions).
	 * 
	 * @param beg Start coordinate (0 based, inclusive)
	 * @param end End coordinate (0 based, exclusive)
	 * @return A list of bins
	 */
	public static ArrayList<Integer> reg2bins(int beg, int end) {
		if (beg >= end) { return new ArrayList<Integer>(0); }
		// any given point will overlap 6 regions, go ahead and allocate a few extra spots by default
		ArrayList<Integer> bins = new ArrayList<Integer>(8);
		int k;
		if (end >= 1<<29) end = 1<<29;
		--end;
		bins.add(0); // everything can overlap the 0th bin!
		for (k =    1 + (beg>>26); k <=    1 + (end>>26); ++k) bins.add(k);
		for (k =    9 + (beg>>23); k <=    9 + (end>>23); ++k) bins.add(k);
		for (k =   73 + (beg>>20); k <=   73 + (end>>20); ++k) bins.add(k);
		for (k =  585 + (beg>>17); k <=  585 + (end>>17); ++k) bins.add(k);
		for (k = 4681 + (beg>>14); k <= 4681 + (end>>14); ++k) bins.add(k);
		return bins;
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
	 * Read one line from the data file.
	 */
	public String readLine() throws IOException {
		return mFp.readLine();
	}

	public Integer getIdForChromosome(final String chromosome) {
		return mChr2tid.get(chromosome);
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
		Integer tid = getIdForChromosome(chr);
		ret[0] = tid == null? -1 : tid.intValue();
		return ret;
	}

	private TIntv getIntv(final String s[]) {
		TIntv intv = new TIntv();
		intv.tid = getIdForChromosome(s[sequenceColumn-1]);
		// begin
		intv.beg = Integer.parseInt(s[beginColumn-1]);
		intv.end = intv.beg;
		if ((mPreset&0x10000) != 0) ++intv.end;
		else --intv.beg;
		if (intv.beg < 0) intv.beg = 0;
		if (intv.end < 1) intv.end = 1;
		if ((mPreset&0xffff) == 0) { // generic
			intv.end = Integer.parseInt(s[endColumn-1]);
		} else if ((mPreset&0xffff) == 1) { // SAM
			String cigar = s[5];
			int cigarLen = 0, i, j;
			for (i = j = 0; i < cigar.length(); ++i) {
				if (cigar.charAt(i) > '9') {
					int op = cigar.charAt(i);
					if (op == 'M' || op == 'D' || op == 'N')
						cigarLen += Integer.parseInt(cigar.substring(j, i));
				}
			}
			intv.end = intv.beg + cigarLen;
		} else if ((mPreset&0xffff) == 2) { // VCF
			String ref = s[3];
			if (ref.length() > 0) intv.end = intv.beg + ref.length();
			// check in the INFO field for an END
			String info = s[7];
			int endOffsetInInfo = -1;
			if (info.startsWith("END=")) {
				endOffsetInInfo = 4;
				intv.end = Integer.parseInt(info.substring(endOffsetInInfo).split(";",2)[0]);
			} else if ((endOffsetInInfo = info.indexOf(";END=")) > 0){
				intv.end = Integer.parseInt(info.substring(endOffsetInInfo+5).split(";",2)[0]);
			}
		}
		return intv;
	}

	public List<String> readHeaders() throws IOException {
		if (headers == null) {
			ArrayList<String> tmpHeaders = new ArrayList<String>(linesToSkip);
			int skiplinesRemaining = linesToSkip;
			mFp.seek(0);
			String line;
			while ((line = mFp.readLine()) != null) {
				skiplinesRemaining--;
				if (skiplinesRemaining >= 0 || line.charAt(0) == metaCharacter) {
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
		private int i, n_seeks;
		private int tid, beg, end;
		private TPair64[] off;
		private long curr_off;
		private boolean iseof;

		public Iterator(final int _tid, final int _beg, final int _end, final TPair64[] _off) {
			i = -1; n_seeks = 0; curr_off = 0; iseof = false;
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
						++n_seeks;
					}
					++i;
				}
				String s;
				if ((s = readLine()) != null) {
					curr_off = mFp.getFilePointer();
					if (s.length() == 0 || s.startsWith(meta)) continue;
					String [] row = s.split("\t");
					TIntv intv;
					intv = getIntv(row);
					if (intv.tid != tid || intv.beg >= end) break; // no need to proceed
					else if (intv.end > beg && intv.beg < end) return row; // overlap; return
				} else break; // end of file
			}
			iseof = true;
			return null;
		}
	};

	public Iterator query(final int tid, final int beg, final int end) {
		TPair64[] off, chunks;
		long min_off;
		TIndex idx = mIndex[tid];
		List<Integer> bins = reg2bins(beg, end);
		int i, l, n_off;
		if (idx.l.length > 0)
			min_off = (beg>>TAD_LIDX_SHIFT >= idx.l.length)? idx.l[idx.l.length-1] : idx.l[beg>>TAD_LIDX_SHIFT];
		else min_off = 0;
		ArrayList<TPair64> offList = new ArrayList<TPair64>();
		for (Integer bin : bins) {
			if ((chunks = idx.b.get(bin)) != null ) {
				for (TPair64 chunk : chunks) {
					if (less64(min_off, chunk.v)) offList.add(chunk);
				}
			}
		}
		if (offList.isEmpty()) return new Iterator(tid, beg, end, new TPair64[0]);
		n_off = offList.size();
		off = (TPair64 []) offList.toArray(new TPair64 [n_off]);

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
			if (!less64(off[i-1].v, off[i].u)) off[i-1] = new TPair64(off[i-1].u, off[i].u);
		// merge adjacent blocks
		for (i = 1, l = 0; i < n_off; ++i) {
			if (off[l].v>>16 == off[i].u>>16) off[l] = new TPair64(off[l].u, off[i].v);
			else {
				++l;
				off[l] = off[i];
			}
		}
		n_off = l + 1;
		// return
		TPair64[] ret = Arrays.copyOf(off, n_off);
		if (ret.length == 1 && ret[0] == null) ret = new TPair64[0]; // not sure how this would happen
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
			String s;
			if (args.length == 1) { // no region is specified; print the whole file
				while ((s = tr.readLine()) != null)
					System.out.println(s);
			} else { // a region is specified; random access
				String [] row;
				TabixReader.Iterator iter = tr.query(args[1]); // get the iterator
				while (iter != null && (row = iter.next()) != null)
					System.out.println(join(row, "\t"));
			}
		} catch (IOException e) {
		}
	}
}
