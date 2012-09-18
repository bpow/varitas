package org.drpowell.tabix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import net.sf.samtools.util.BlockCompressedFilePointerUtil;
import net.sf.samtools.util.BlockCompressedOutputStream;
import net.sf.samtools.util.StringUtil;

import org.drpowell.tabix.TabixIndex.TabixConfig;

public class TabixCompressorAndWriter {
	private final BlockCompressedOutputStream bcos;
	private final TabixIndex tabix;
	private final int maxColOfInterest;
	private int tidCurr = -1;
	private BinIndex currBinningIndex = new BinIndex();
	private LinearIndex currLinearIndex = new LinearIndex();
	private boolean done = false;
	
	public TabixCompressorAndWriter(String fileName, TabixConfig config) throws IOException {
		String ouf = fileName + ".gz";
		bcos = new BlockCompressedOutputStream(ouf);
		this.tabix = new TabixIndex(config, new File(ouf));
		maxColOfInterest = calcMaxCol();
	}

	public void addLine(String line) throws IOException {
		if (done) { throw new IllegalStateException("Tried to add rows to an index after finish() was called"); }
		line += "\n";
		if (line.startsWith(tabix.config.commentString)) {
			bcos.write(line.getBytes());
			return;
		}
		String [] row = new String[maxColOfInterest];
		StringUtil.split(line, row, '\t');
		GenomicInterval intv = tabix.getInterval(row);
		if (intv.getSequenceId() != tidCurr && tidCurr >= 0) {
			finishPrevChromosome(tidCurr);
		}
		tidCurr = intv.getSequenceId();
		final long startOffset = bcos.getFilePointer();
		bcos.write(line.getBytes());
		final long endOffset = bcos.getFilePointer();
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
	
	private final int calcMaxCol() {
		// as an optimization, what is the largest column which will be used in indexing?
		if (tabix.config.preset == TabixIndex.TBX_PRESET_VCF) {
			return 8; // VCF files use the INFO field for ends of structural variants
		}
		int max = tabix.config.seqCol;
		max = Math.max(max, tabix.config.beginCol);
		max = Math.max(max, tabix.config.endCol);
		return max;
	}
	
	public void finish() throws IOException {
		finishPrevChromosome(tidCurr);
		done = true;
		bcos.close();
	}
	
	public TabixIndex getIndex() {
		return tabix;
	}
	
	/**
	 * A convenience method to read/process/compress/index from a BufferedReader.
	 * 
	 * This just reads each line from the BufferedReader and calls addLine()
	 * @param reader
	 * @return the constructed TabixIndex
	 * @throws IOException
	 */
	public TabixIndex buildIndex(BufferedReader reader) throws IOException {
		String line = null;
		while ((line = reader.readLine()) != null) {
			addLine(line);
		}
		this.finish();
		return getIndex();
	}

	public static void main(String args[]) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(args[0]));
		TabixCompressorAndWriter tcaw = new TabixCompressorAndWriter(args[0], TabixConfig.VCF);
		String line = null;
		while ((line = br.readLine()) != null) {
			tcaw.addLine(line);
		}
		tcaw.finish();
		tcaw.getIndex().save();
		br.close();
	}

}
