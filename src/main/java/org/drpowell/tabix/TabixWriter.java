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
import java.io.IOException;
import java.util.List;

import net.sf.samtools.util.BlockCompressedInputStream;
import net.sf.samtools.util.BlockCompressedOutputStream;

import org.drpowell.tabix.TabixIndex.Chunk;
import org.drpowell.tabix.TabixIndex.TabixConfig;


/**
 * Tabix writer, based on Heng Li's C implementation.
 *
 * @author tarkvara
 * @author Bradford Powell
 */
public class TabixWriter {
    //private static final Logger LOG = Logger.getLogger(TabixWriter.class.getCanonicalName());

    private final String fileName;
    private final TabixConfig conf;
    private TabixIndex tabix;
   
    public TabixWriter(File fn, TabixConfig conf) throws IOException {
    	fileName = fn.getAbsolutePath();
        this.conf = conf;
        tabix = new TabixIndex(conf, new File(fileName));
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
            if (lineno <= conf.linesToSkip || str.charAt(0) == conf.commentChar) {
                last_off = fp.getFilePointer();
                continue;
            }
            GenomicInterval intv = tabix.getInterval(str.split("\t"));
            if ( intv.getBegin()<0 || intv.getEnd()<0 ) {
                throw new Exception("The indexes overlap or are out of bounds.");
            }
            if (last_tid != intv.getSequenceId()) { // change of chromosomes
                if (last_tid>intv.getSequenceId() ) {
                    throw new Exception(String.format(
                    		"The chromosome blocks are not continuous at line %d, is the file sorted? [pos %d].",
                    		lineno, intv.getBegin()+1));
                }
                last_tid = intv.getSequenceId();
                last_bin = 0xffffffff;
            } else if (last_coor > intv.getBegin()) {
                throw new Exception(String.format("File out of order at line %d.", lineno));
            }
            long tmp = insertLinear(tabix.linearIndex.get(intv.getSequenceId()), intv.getBegin(), intv.getEnd(), last_off);
            if (last_off == 0) offset0 = tmp;
            if (intv.getBin() != last_bin) { // then possibly write the binning index
                if (save_bin != 0xffffffff) { // save_bin==0xffffffffu only happens to the first record
                    insertBinning(tabix.binningIndex.get(save_tid), save_bin, save_off, last_off);
                }
                save_off = last_off;
                save_bin = last_bin = intv.getBin();
                save_tid = intv.getSequenceId();
                if (save_tid < 0) break;
            }
            if (fp.getFilePointer() <= last_off) {
                throw new Exception(String.format("Bug in BGZF: %x < %x.", fp.getFilePointer(), last_off));
            }
            last_off = fp.getFilePointer();
            last_coor = intv.getBegin();
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

    private void insertBinning(BinIndex binningForChr, int bin, long beg, long end) {
    	binningForChr.getWithNew(bin).add(new Chunk(beg, end));
    }

    private long insertLinear(LinearIndex linearForChr, int beg, int end, long offset) {
    	beg = beg >> TabixIndex.TBX_LIDX_SHIFT;
        end = (end - 1) >> TabixIndex.TBX_LIDX_SHIFT;

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
            BinIndex binningForChr = tabix.binningIndex.get(i);
            for (Integer binNum: binningForChr.bins()) {
                List<Chunk> p = binningForChr.get(binNum);
                int m = 0;
                for (int l = 1; l < p.size(); l++) {
                    if (p.get(m).end >> 16 == p.get(l).begin >> 16) {
                    	p.set(m, new Chunk(p.get(m).begin, p.get(l).end));
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
    	TabixWriter tw = new TabixWriter(f, TabixConfig.VCF);
    	tw.createIndex();
    	
    }

}
