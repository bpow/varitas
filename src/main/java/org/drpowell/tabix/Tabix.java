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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.samtools.LinearIndex;
import net.sf.samtools.util.BinaryCodec;
import net.sf.samtools.util.BlockCompressedOutputStream;


public class Tabix {

	public static final int MAX_BIN = 37450;
	public static final int TAD_MIN_CHUNK_GAP = 32768;
	public static final int TAD_LIDX_SHIFT = 14;
    public static final int TI_PRESET_GENERIC = 0;
    public static final int TI_PRESET_SAM = 1;
    public static final int TI_PRESET_VCF = 2;
    public static final int TI_FLAG_UCSC = 0x10000;

    // TODO - split out config class, since this class now does much more...
    public static final Tabix GFF_CONF = new Tabix(0, 1, 4, 5, '#', 0);
    public static final Tabix BED_CONF = new Tabix(TI_FLAG_UCSC, 1, 2, 3, '#', 0);
    public static final Tabix PSLTBL_CONF = new Tabix(TI_FLAG_UCSC, 15, 17, 18, '#', 0);
    public static final Tabix SAM_CONF = new Tabix(TI_PRESET_SAM, 3, 4, 0, '@', 0);
    public static final Tabix VCF_CONF = new Tabix(TI_PRESET_VCF, 1, 2, 0, '#', 0);
	private static final int MAX_BYTE_BUFFER = 8;

    public final int preset;
    public final int seqColumn;
    public final int startColumn;
    public final int endColumn;
    public final char commentChar;
    public final String comment;
    public final int linesToSkip;
	LinkedHashMap<String, Integer> mChr2tid = new LinkedHashMap<String, Integer>(50);
	private final ByteBuffer buffer;

	/** The binning index. */
    List<ReferenceBinIndex> binningIndex = new ArrayList<ReferenceBinIndex>();

    /** The linear index. */
    List<ReferenceLinearIndex> linearIndex = new ArrayList<ReferenceLinearIndex>();

    public static class Chunk implements Comparable<Chunk> {
		public final long u;
		public final long v;
		public Chunk(final long _u, final long _v) {
			u = _u; v = _v;
		}
		public int compareTo(final Chunk p) {
			return u == p.u? 0 : ((u < p.u) ^ (u < 0) ^ (p.u < 0))? -1 : 1; // unsigned 64-bit comparison
		}
	};
	
	protected static class GenomicInterval {
		int tid, bin;
		int beg, end;
	};

	protected static class ReferenceBinIndex extends LinkedHashMap<Integer, List<Chunk>> {
		private static final long serialVersionUID = 1L;
		public ReferenceBinIndex(int capacity, float loadFactor) {
			super(capacity, loadFactor);
		}
		public ReferenceBinIndex() {
			super(Tabix.MAX_BIN, 1.0f);
		}
		public ReferenceBinIndex(ReferenceBinIndex other) {
			// a new clone with size sufficient to hold the contents of "other"
			super(other);
		}
		public List<Chunk> getWithNew(int i) {
			List<Chunk> out = get(i);
			if (out == null) {
				out = new ArrayList<Chunk>(); // TODO- presize or change to LinkedList
			}
			put(i, out);
			return out;
		}
	}

	protected static class ReferenceLinearIndex extends AbstractList<Long> {
		private long[] index;
		int size = 0;
		public ReferenceLinearIndex() {
			index = new long[LinearIndex.MAX_LINEAR_INDEX_SIZE];
		}
		public ReferenceLinearIndex(int n_linear) {
			index = new long[n_linear];
		}
		public long getPrimitive(int i) { return index[i]; }
		public long setPrimitive(int pos, long l) {
			long old = index[pos];
			if (pos >= size) {
				size = pos+1;
			}
			index[pos] = l;
			return old;
		}
		@Override
		public Long get(int i) {
			return index[i];
		}
		@Override
		public int size() {
			return size;
		}
		@Override
		public Long set(int pos, Long l) {
			return setPrimitive(pos, l);
		}
		@Override
		public void add(int i, Long l) {
			this.setPrimitive(size, l);
		}
		public ReferenceLinearIndex(ReferenceLinearIndex old) {
			size = old.size();
			index = new long[size];
			System.arraycopy(old.index, 0, index, 0, size);
		}
		public void clear() {
			Arrays.fill(index, 0L);
			size = 0;
		}
	}

	public Tabix(int preset, int seqColumn, int startColumn, int endColumn, char commentChar, int linesToSkip) {
        this.preset = preset;
        this.seqColumn = seqColumn;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
        this.commentChar = commentChar;
        comment = Character.toString(commentChar);
        this.linesToSkip = linesToSkip;
        buffer = ByteBuffer.allocate(MAX_BYTE_BUFFER);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
	
	public Integer getIdForChromosome(final String chromosome) {
		Integer tid = mChr2tid.get(chromosome);
		if (tid == null) {
			// adding a new sequence
			tid = mChr2tid.size();
            mChr2tid.put(chromosome, tid);

            // Expand our indices.
            binningIndex.add(new ReferenceBinIndex());
            linearIndex.add(new ReferenceLinearIndex());
		}
		return tid;
	}
	
    public static final int reg2bin(int beg, int end) {
    	--end;
    	if (beg>>14 == end>>14) return 4681 + (beg>>14);
    	if (beg>>17 == end>>17) return  585 + (beg>>17);
    	if (beg>>20 == end>>20) return   73 + (beg>>20);
    	if (beg>>23 == end>>23) return    9 + (beg>>23);
    	if (beg>>26 == end>>26) return    1 + (beg>>26);
    	return 0;
    }
    
	public GenomicInterval getInterval(final String s[]) {
		GenomicInterval intv = new GenomicInterval();
		intv.tid = getIdForChromosome(s[seqColumn-1]);
		// begin
		intv.beg = Integer.parseInt(s[startColumn-1]);
		intv.end = intv.beg;
		if ((preset&TI_FLAG_UCSC) != 0) ++intv.end;
		else --intv.beg;
		if (intv.beg < 0) intv.beg = 0;
		if (intv.end < 1) intv.end = 1;
		if ((preset&0xffff) == 0) { // generic
			intv.end = Integer.parseInt(s[endColumn-1]);
		} else if ((preset&0xffff) == TI_PRESET_SAM) { // SAM
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
		} else if ((preset&0xffff) == TI_PRESET_VCF) { // VCF
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
		intv.bin = reg2bin(intv.beg, intv.end);
		return intv;
	}
	
    protected void saveIndex(BlockCompressedOutputStream bcos) throws IOException {
    	BinaryCodec codec = new BinaryCodec(bcos);
    	codec.writeString("TBI\1", false, false);
        codec.writeInt(binningIndex.size());

        // Write the ti_conf_t
        codec.writeInt(preset);
        codec.writeInt(seqColumn);
        codec.writeInt(startColumn);
        codec.writeInt(endColumn);
        codec.writeInt(commentChar);
        codec.writeInt(linesToSkip);

        // Write sequence dictionary.  Since mChr2tid is a LinkedHashmap, the keyset
        // will be returned in insertion order.
        int l = 0;
        for (String k: mChr2tid.keySet()) {
            l += k.length() + 1;
        }
        codec.writeInt(l);
        for (String k: mChr2tid.keySet()) {
        	codec.writeString(k, false, true);
        }

        for (int i = 0; i < mChr2tid.size(); i++) {
            Map<Integer, List<Chunk>> binningForChr = binningIndex.get(i);
            
            // Write the binning index.
            codec.writeInt(binningForChr.size());
            for (int k: binningForChr.keySet()) {
                List<Chunk> p = binningForChr.get(k);
                codec.writeInt(k);
                codec.writeInt(p.size());
                for (Chunk bin: p) {
                    codec.writeLong(bin.u);
                    codec.writeLong(bin.v);
                }
            }
            // Write the linear index.
            List<Long> linearForChr = linearIndex.get(i);
            codec.writeInt(linearForChr.size());
            for (int x = 0; x < linearForChr.size(); x++) {
                codec.writeLong(linearForChr.get(x));
            }
        }
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

}
