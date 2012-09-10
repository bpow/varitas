package org.drpowell.tabix;

/* The MIT License

   Copyright (c) 2010 Broad Institute.
   Portions Copyright (c) 2011 University of Toronto.
   Portions Copyright (c) 2012 Baylor College of Medicine

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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.sf.samtools.util.BlockCompressedInputStream;
import net.sf.samtools.util.BlockCompressedOutputStream;

import org.drpowell.tabix.Tabix.GenomicInterval;
import org.drpowell.tabix.Tabix.Chunk;


/**
 * Tabix writer, based on Heng Li's C implementation.
 *
 * @author tarkvara
 * @author Bradford Powell
 */
public class TabixWriter {
    //private static final Logger LOG = Logger.getLogger(TabixWriter.class.getCanonicalName());

    private final String fileName;
    private Tabix tabix;
   
    public TabixWriter(File fn, Tabix conf) throws Exception {
    	fileName = fn.getAbsolutePath();
        tabix = conf;
    }

    public void createIndex() throws Exception {
        BlockCompressedInputStream fp = new BlockCompressedInputStream(new FileInputStream(fileName));
        makeIndex(fp);
        fp.close();
        File indexFile = new File(fileName + ".tbi");
        BlockCompressedOutputStream fpidx = new BlockCompressedOutputStream(indexFile);
        tabix.saveIndex(fpidx);
        fpidx.close();
    }

    private void makeIndex(BlockCompressedInputStream fp) throws Exception {
    	int last_bin, save_bin;
    	int last_coor, last_tid, save_tid;
    	long save_off, last_off, lineno = 0, offset0 = (long)-1;
    	String str;

    	save_bin = save_tid = last_tid = last_bin = 0xffffffff;         // Was unsigned in C implementation.
    	save_off = last_off = 0;
        last_coor = 0xffffffff;    // Should be unsigned.
        while ((str = fp.readLine()) != null) {
            ++lineno;
            if (lineno <= tabix.linesToSkip || str.charAt(0) == tabix.commentChar) {
                last_off = fp.getFilePointer();
                continue;
            }
            GenomicInterval intv = tabix.getInterval(str.split("\t"));
            if ( intv.beg<0 || intv.end<0 ) {
                throw new Exception("The indexes overlap or are out of bounds.");
            }
            if (last_tid != intv.tid) { // change of chromosomes
                if (last_tid>intv.tid ) {
                    throw new Exception(String.format("The chromosome blocks are not continuous at line %d, is the file sorted? [pos %d].", lineno, intv.beg+1));
                }
                last_tid = intv.tid;
                last_bin = 0xffffffff;
            } else if (last_coor > intv.beg) {
                throw new Exception(String.format("File out of order at line %d.", lineno));
            }
            long tmp = insertLinear(tabix.linearIndex.get(intv.tid), intv.beg, intv.end, last_off);
            if (last_off == 0) offset0 = tmp;
            if (intv.bin != last_bin) { // then possibly write the binning index
                if (save_bin != 0xffffffff) { // save_bin==0xffffffffu only happens to the first record
                    insertBinning(tabix.binningIndex.get(save_tid), save_bin, save_off, last_off);
                }
                save_off = last_off;
                save_bin = last_bin = intv.bin;
                save_tid = intv.tid;
                if (save_tid < 0) break;
            }
            if (fp.getFilePointer() <= last_off) {
                throw new Exception(String.format("Bug in BGZF: %x < %x.", fp.getFilePointer(), last_off));
            }
            last_off = fp.getFilePointer();
            last_coor = intv.beg;
	}
	if (save_tid >= 0) insertBinning(tabix.binningIndex.get(save_tid), save_bin, save_off, fp.getFilePointer());
	mergeChunks();
	fillMissing();
	if (offset0 != (long)-1 && !tabix.linearIndex.isEmpty() && tabix.linearIndex.get(0) != null) {
            int beg = (int)(offset0>>32), end = (int)(offset0 & 0xffffffff);
            for (int i = beg; i <= end; ++i) {
                tabix.linearIndex.get(0).set(i, 0L);
            }
	}
    }

    private void insertBinning(Map<Integer, List<Chunk>> binningForChr, int bin, long beg, long end) {
        if (!binningForChr.containsKey(bin)) {
            binningForChr.put(bin, new ArrayList<Chunk>());
        }
        List<Chunk> list = binningForChr.get(bin);
        list.add(new Chunk(beg, end));
    }

    private long insertLinear(List<Long> linearForChr, int beg, int end, long offset) {
	beg = beg >> Tabix.TAD_LIDX_SHIFT;
	end = (end - 1) >> Tabix.TAD_LIDX_SHIFT;

        // Expand the array if necessary.
        int newSize = Math.max(beg, end) + 1;
        while (linearForChr.size() < newSize) {
            linearForChr.add(0L);
        }
	if (beg == end) {
            if (linearForChr.get(beg) == 0L) {
                linearForChr.set(beg, offset);
            }
	} else {
            for (int i = beg; i <= end; ++i) {
                if (linearForChr.get(i) == 0L) {
                    linearForChr.set(i, offset);
                }
            }
	}
	return (long)beg<<32 | end;
    }

    private void mergeChunks() {
        for (int i = 0; i < tabix.binningIndex.size(); i++) {
            Map<Integer, List<Chunk>> binningForChr = tabix.binningIndex.get(i);
            for (Integer k: binningForChr.keySet()) {
                List<Chunk> p = binningForChr.get(k);
                int m = 0;
                for (int l = 1; l < p.size(); l++) {
                    if (p.get(m).v >> 16 == p.get(l).u >> 16) {
                    	p.set(m, new Chunk(p.get(m).u, p.get(l).v));
                    } else {
                        p.set(++m, p.get(l));
                    }
                }
                while (p.size() > m + 1) {
                    p.remove(p.size() - 1);
                }
            }
        }
    }

    private void fillMissing() {
	for (int i = 0; i < tabix.linearIndex.size(); ++i) {
            List<Long> linearForChr = tabix.linearIndex.get(i);
            for (int j = 1; j < linearForChr.size(); ++j) {
                if (linearForChr.get(j) == 0) {
                    linearForChr.set(j, linearForChr.get(j-1));
                }
            }
        }
    }
    
    public static void main(String args[]) throws Exception {
    	File f = new File(args[0]);
    	TabixWriter tw = new TabixWriter(f, Tabix.VCF_CONF);
    	tw.createIndex();
    	
    }

}
