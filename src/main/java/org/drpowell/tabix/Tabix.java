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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.samtools.util.BlockCompressedOutputStream;


public class Tabix {

	public static final int MAX_BIN = 37450;
	public static final int TAD_MIN_CHUNK_GAP = 32768;
	public static final int TAD_LIDX_SHIFT = 14;
    public static final int TI_PRESET_GENERIC = 0;
    public static final int TI_PRESET_SAM = 1;
    public static final int TI_PRESET_VCF = 2;
    public static final int TI_FLAG_UCSC = 0x10000;
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

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
    List<Map<Integer, List<Pair64Unsigned>>> binningIndex = new ArrayList<Map<Integer, List<Pair64Unsigned>>>();

    /** The linear index. */
    List<List<Long>> linearIndex = new ArrayList<List<Long>>();

    public static class Pair64Unsigned implements Comparable<Pair64Unsigned> {
		public final long u;
		public final long v;
		public Pair64Unsigned(final long _u, final long _v) {
			u = _u; v = _v;
		}
		public int compareTo(final Pair64Unsigned p) {
			return u == p.u? 0 : ((u < p.u) ^ (u < 0) ^ (p.u < 0))? -1 : 1; // unsigned 64-bit comparison
		}
	};
	
	protected static class GenomicInterval {
		int tid, bin;
		int beg, end;
	};

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
            binningIndex.add(new HashMap<Integer, List<Pair64Unsigned>>());
            linearIndex.add(new ArrayList<Long>());
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
		if ((preset&0x10000) != 0) ++intv.end;
		else --intv.beg;
		if (intv.beg < 0) intv.beg = 0;
		if (intv.end < 1) intv.end = 1;
		if ((preset&0xffff) == 0) { // generic
			intv.end = Integer.parseInt(s[endColumn-1]);
		} else if ((preset&0xffff) == 1) { // SAM
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
		} else if ((preset&0xffff) == 2) { // VCF
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
	
    protected void saveIndex(BlockCompressedOutputStream fp) throws IOException {
        fp.write("TBI\1".getBytes(LATIN1));
        writeInt(fp, binningIndex.size());

        // Write the ti_conf_t
        writeInt(fp, preset);
        writeInt(fp, seqColumn);
        writeInt(fp, startColumn);
        writeInt(fp, endColumn);
        writeInt(fp, commentChar);
        writeInt(fp, linesToSkip);

        // Write sequence dictionary.  Since mChr2tid is a LinkedHashmap, the keyset
        // will be returned in insertion order.
        int l = 0;
        for (String k: mChr2tid.keySet()) {
            l += k.length() + 1;
        }
        writeInt(fp, l);
        for (String k: mChr2tid.keySet()) {
            fp.write(k.getBytes(LATIN1));
            fp.write(0);
        }

        for (int i = 0; i < mChr2tid.size(); i++) {
            Map<Integer, List<Pair64Unsigned>> binningForChr = binningIndex.get(i);
            
            // Write the binning index.
            writeInt(fp, binningForChr.size());
            for (int k: binningForChr.keySet()) {
                List<Pair64Unsigned> p = binningForChr.get(k);
                writeInt(fp, k);
                writeInt(fp, p.size());
                for (Pair64Unsigned bin: p) {
                    writeLong(fp, bin.u);
                    writeLong(fp, bin.v);
                }
            }
            // Write the linear index.
            List<Long> linearForChr = linearIndex.get(i);
            writeInt(fp, linearForChr.size());
            for (int x = 0; x < linearForChr.size(); x++) {
                writeLong(fp, linearForChr.get(x));
            }
        }
    }

    public static void writeInt(final OutputStream os, int value) throws IOException {
        byte[] buf = new byte[4];
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
        os.write(buf);
    }

    public static void writeLong(final OutputStream os, long value) throws IOException {
        byte[] buf = new byte[8];
        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).putLong(value);
        os.write(buf);
    }

}
