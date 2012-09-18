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
import java.util.LinkedHashMap;
import java.util.List;

import net.sf.samtools.util.BinaryCodec;
import net.sf.samtools.util.BlockCompressedInputStream;
import net.sf.samtools.util.BlockCompressedOutputStream;


public class TabixIndex {

	public static final int TBX_MAX_BIN = 37450;
	public static final int TBX_MIN_CHUNK_GAP = 32768;
	public static final int TBX_LIDX_SHIFT = 14;

	public static class TabixConfig {
		public final int preset, seqCol, beginCol, endCol, commentChar, linesToSkip;
		public final String commentString;
		public TabixConfig (int p, int sc, int bc, int ec, int comment, int skipLines) {
			preset = p; seqCol = sc; beginCol = bc; endCol = ec; commentChar = comment; linesToSkip = skipLines; 
			commentString = Character.toString((char) commentChar);
		}
		public static final TabixConfig GFF = new TabixConfig(0, 1, 4, 5, '#', 0);
	    public static final TabixConfig BED = new TabixConfig(TBX_FLAG_UCSC, 1, 2, 3, '#', 0);
	    public static final TabixConfig PSLTBL = new TabixConfig(TBX_FLAG_UCSC, 15, 17, 18, '#', 0);
	    public static final TabixConfig SAM = new TabixConfig(TBX_PRESET_SAM, 3, 4, 0, '@', 0);
	    public static final TabixConfig VCF = new TabixConfig(TBX_PRESET_VCF, 1, 2, 0, '#', 0);
	}
	public static final int TBX_PRESET_GENERIC = 0;
    public static final int TBX_PRESET_SAM = 1;
    public static final int TBX_PRESET_VCF = 2;
    public static final int TBX_FLAG_UCSC = 0x10000;
    public BlockCompressedInputStream indexedFile;


    public final TabixConfig config;
    LinkedHashMap<String, Integer> mChr2tid = new LinkedHashMap<String, Integer>(50);

	/** The binning index. */
    List<BinIndex> binningIndex = new ArrayList<BinIndex>();

    /** The linear index. */
    List<LinearIndex> linearIndex = new ArrayList<LinearIndex>();

    /**
     * A BAM/Tabix chunk consists of two unsigned 64-bit integers which mark the
     * beginning and end (in virtual offset coordinates) of a range in the 
     * BlockCompressed file which is indexed.
     * 
     * Java doesn't have an unsigned long type, but as long as you don't divide, things
     * should be OK.
     * @author bpow
     *
     */
    public static class Chunk implements Comparable<Chunk> {
		public final long begin;
		public final long end;
		public Chunk(final long begin, final long end) {
			this.begin = begin; this.end = end;
		}
		public int compareTo(final Chunk p) {
			if (begin == p.begin) {
				return cmpUInt64(end, p.end);
			} else {
				return cmpUInt64(begin, p.begin);
			}
		}
		public static final int cmpUInt64(long u, long v) {
			if (u == v) return 0;
			return ((u<v) ^ (u<0) ^ (v<0))? -1 : 1;
		}
	};
	
	public TabixIndex(int preset, int seqColumn, int startColumn, int endColumn, char commentChar, int linesToSkip) {
        this(new TabixConfig(preset, seqColumn, startColumn, endColumn, commentChar, linesToSkip));
    }
	
	public TabixIndex(final TabixConfig conf) {
		config = conf;
	}
	
	public Integer getIdForChromosome(final String chromosome) {
		Integer tid = mChr2tid.get(chromosome);
		if (tid == null) {
			// adding a new sequence
			tid = mChr2tid.size();
            mChr2tid.put(chromosome, tid);

            // Expand our indices.
            binningIndex.add(new BinIndex());
            linearIndex.add(new LinearIndex());
		}
		return tid;
	}

	/**
	 * Parse a region in the format of "chr1", "chr1:100" or "chr1:100-1000"
	 *
	 * @param reg Region string
	 * @return An array where the three elements are sequence_id,
	 *         region_begin and region_end. On failure, returns null.
	 */
	public GenomicInterval parseInterval(final String reg) {
		int colon, hyphen;
		String chr;
		colon = reg.lastIndexOf(':'); hyphen = reg.lastIndexOf('-');
		chr = colon >= 0? reg.substring(0, colon) : reg;
		Integer tid = getIdForChromosome(chr);
		if (tid == null) return null;
		
		return new GenomicInterval(tid,
					colon >= 0? Integer.parseInt(reg.substring(colon+1, hyphen >= 0? hyphen : reg.length())) - 1 : 0,
					hyphen >= 0? Integer.parseInt(reg.substring(hyphen+1)) : 0x7fffffff);
	}

    public GenomicInterval getInterval(final String s[]) {
		int sequenceId = getIdForChromosome(s[config.seqCol-1]);
		// begin
		int beg = Integer.parseInt(s[config.beginCol-1]);
		int end = beg;
		if ((config.preset&TBX_FLAG_UCSC) != 0) ++end;
		else --beg;
		if (beg < 0) beg = 0;
		if (end < 1) end = 1;
		if ((config.preset&0xffff) == 0) { // generic
			end = Integer.parseInt(s[config.endCol-1]);
		} else if ((config.preset&0xffff) == TBX_PRESET_SAM) { // SAM
			String cigar = s[5];
			int cigarLen = 0, i, j;
			for (i = j = 0; i < cigar.length(); ++i) {
				if (cigar.charAt(i) > '9') {
					int op = cigar.charAt(i);
					if (op == 'M' || op == 'D' || op == 'N')
						cigarLen += Integer.parseInt(cigar.substring(j, i));
				}
			}
			end = beg + cigarLen;
		} else if ((config.preset&0xffff) == TBX_PRESET_VCF) { // VCF
			String ref = s[3];
			if (ref.length() > 0) end = beg + ref.length();
			// check in the INFO field for an END
			String info = s[7];
			int endOffsetInInfo = -1;
			if (info.startsWith("END=")) {
				endOffsetInInfo = 4;
				end = Integer.parseInt(info.substring(endOffsetInInfo).split(";",2)[0]);
			} else if ((endOffsetInInfo = info.indexOf(";END=")) > 0){
				end = Integer.parseInt(info.substring(endOffsetInInfo+5).split(";",2)[0]);
			}
		}
		return new GenomicInterval(beg, end, sequenceId);
	}
	
    protected void saveIndex(BlockCompressedOutputStream bcos) throws IOException {
    	BinaryCodec codec = new BinaryCodec(bcos);
    	codec.writeString("TBI\1", false, false);
        codec.writeInt(binningIndex.size());

        // Write the ti_conf_t
        codec.writeInt(config.preset);
        codec.writeInt(config.seqCol);
        codec.writeInt(config.beginCol);
        codec.writeInt(config.endCol);
        codec.writeInt(config.commentChar);
        codec.writeInt(config.linesToSkip);

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
            BinIndex binningForChr = binningIndex.get(i);
            
            // Write the binning index.
            codec.writeInt(binningForChr.size());
            for (int binNum: binningForChr.bins()) {
                List<Chunk> p = binningForChr.get(binNum);
                codec.writeInt(binNum);
                codec.writeInt(p.size());
                for (Chunk bin: p) {
                    codec.writeLong(bin.begin);
                    codec.writeLong(bin.end);
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

}
