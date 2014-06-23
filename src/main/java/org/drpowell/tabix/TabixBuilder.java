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

import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.drpowell.tabix.TabixIndex.TabixConfig;
import org.drpowell.util.LineIterator;

public class TabixBuilder {
	private final TabixIndex tabix;
	private int tidCurr = -1;
	private BinIndex currBinningIndex = new BinIndex();
	private LinearIndex currLinearIndex = new LinearIndex();
	
	// FIXME- arguably could just stick with '\n'...
	private static final byte [] LINE_SEPARATOR = System.getProperty("line.separator").getBytes();
	
	private TabixBuilder(String fileName, TabixConfig config) throws IOException {
		this.tabix = new TabixIndex(config, new File(fileName));
	}

	private void addLine(final String line, final long startOffset, final long endOffset) {
		DelimitedString row = new DelimitedString(line, '\t');
		GenomicInterval intv = tabix.getInterval(row);
		if (intv.getSequenceId() != tidCurr && tidCurr >= 0) {
			finishPrevChromosome(tidCurr);
		}
		tidCurr = intv.getSequenceId();
		TabixIndex.Chunk chunk = new TabixIndex.Chunk(startOffset, endOffset);
		
		// process binning index
		List<TabixIndex.Chunk> bin = currBinningIndex.getWithNew(intv.getBin());
		// check for overlapping chunks
		if (bin.isEmpty()) {
			bin.add(chunk);
		} else {
			TabixIndex.Chunk lastChunk = bin.get(bin.size()-1);
			if (BlockCompressedFilePointerUtil.areInSameOrAdjacentBlocks(lastChunk.end, chunk.begin)) {
				bin.set(bin.size()-1, new TabixIndex.Chunk(lastChunk.begin, chunk.end)); // coalesce
			} else {
				bin.add(chunk);
			}
		}
		
		// process linear index
		int startWindow = LinearIndex.convertToLinearIndexOffset(intv.getBegin());
		int endWindow = LinearIndex.convertToLinearIndexOffset(intv.getEnd());
		for (int win = startWindow; win <= endWindow; win++) {
			if (currLinearIndex.getPrimitive(win) == 0 || chunk.begin < currLinearIndex.getPrimitive(win)) {
				currLinearIndex.setPrimitive(win, chunk.begin);
			}
		}
	}
	
	private void finishPrevChromosome(int tidPrev) {
		if (currBinningIndex.isEmpty()) return; // saw nothing for this reference
		
		tabix.linearIndex.set(tidPrev, currLinearIndex.getCompacted());
		currLinearIndex.clear();
		
		// make things as compact as possible...
		tabix.binningIndex.set(tidPrev, new BinIndex(currBinningIndex));
		currBinningIndex = new BinIndex();
	}
	
	public void finish() throws IOException {
		finishPrevChromosome(tidCurr);
	}
	
	public static TabixIndex buildIndex(BufferedReader reader, String compressedFilename, TabixConfig config) throws IOException {
		return buildIndex(new LineIterator(reader), compressedFilename, config);
	}
	
	public static TabixIndex buildIndex(Iterator<String> input, String compressedFileName, TabixConfig config) throws IOException {
		TabixBuilder builder = new TabixBuilder(compressedFileName, config);
		BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(new File(compressedFileName));
		long startOffset, endOffset;
		while (input.hasNext()) {
			String line = input.next();
			if (line.startsWith(config.commentString)) {
				bcos.write(line.getBytes());
				bcos.write(LINE_SEPARATOR);
				continue;
			}
			startOffset = bcos.getFilePointer();
			bcos.write(line.getBytes());
			bcos.write(LINE_SEPARATOR);
			endOffset = bcos.getFilePointer();
			builder.addLine(line, startOffset, endOffset);
		}
		builder.finish();
		bcos.close();
		return builder.tabix;
	}
	
	public static TabixIndex buildIndex(String compressedFile, TabixConfig config) throws IOException {
		TabixBuilder builder = new TabixBuilder(compressedFile, config);
		BlockCompressedInputStream bcis = new BlockCompressedInputStream(new File(compressedFile));
		long startOffset = 0, endOffset;
		String line = null;
		while ((line = bcis.readLine()) != null) {
			endOffset = bcis.getFilePointer();
			if (!line.startsWith(config.commentString)) {
				builder.addLine(line, startOffset, endOffset);
			}
			startOffset = endOffset;
		}
		builder.finish();
		bcis.close();
		return builder.tabix;
	}
	
	public static void main(String args[]) throws IOException {
		String inf = args[0];
		TabixIndex index;
		if (inf.endsWith(".gz")) {
			index = buildIndex(inf, TabixConfig.VCF);
		} else {
			BufferedReader br = new BufferedReader(new FileReader(inf));
			index = buildIndex(br, inf + ".gz", TabixConfig.VCF);
			br.close();
		}
		index.save();
	}

}
