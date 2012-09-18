package org.drpowell.tabix;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.drpowell.tabix.Tabix.TabixConfig;

import net.sf.samtools.LinearIndex;
import net.sf.samtools.util.BlockCompressedFilePointerUtil;
import net.sf.samtools.util.BlockCompressedOutputStream;
import net.sf.samtools.util.StringUtil;

public class TabixCompressorAndWriter {
	private final BlockCompressedOutputStream bcos;
	private final String fileName;
	private final Tabix tabix;
	private final int maxColOfInterest;
	private int tidCurr = -1;
	private Tabix.ReferenceBinIndex currBinningIndex = new Tabix.ReferenceBinIndex();
	private Tabix.ReferenceLinearIndex currLinearIndex = new Tabix.ReferenceLinearIndex();
	
	public TabixCompressorAndWriter(String fileName, TabixConfig config) {
		this.fileName = fileName;
		bcos = new BlockCompressedOutputStream(fileName + ".gz");
		this.tabix = new Tabix(config);
		maxColOfInterest = calcMaxCol();
	}

	public void addLine(String line) throws IOException {
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
		Tabix.Chunk chunk = new Tabix.Chunk(startOffset, endOffset);
		
		// process binning index
		List<Tabix.Chunk> bin = currBinningIndex.getWithNew(intv.getBin());
		// check for overlapping chunks
		if (bin.isEmpty()) {
			bin.add(chunk);
		} else {
			Tabix.Chunk lastChunk = bin.get(bin.size()-1);
			if (BlockCompressedFilePointerUtil.areInSameOrAdjacentBlocks(lastChunk.end, chunk.begin)) {
				bin.set(bin.size()-1, new Tabix.Chunk(lastChunk.begin, chunk.end)); // coalesce
			} else {
				bin.add(chunk);
			}
		}
		
		// process linear index
		// TODO - do I need to check for off-by-one changes?
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
		
		tabix.linearIndex.set(tidPrev, new Tabix.ReferenceLinearIndex(currLinearIndex));
		tabix.linearIndex.get(tidPrev).fillZeros();
		currLinearIndex.clear();
		
		// make things as compact as possible...
		tabix.binningIndex.set(tidPrev, new Tabix.ReferenceBinIndex(currBinningIndex));
		currBinningIndex.clear();
		
	}
	
	private final int calcMaxCol() {
		// as an optimization, what is the largest column which will be used in indexing?
		if (tabix.config.preset == Tabix.TBX_PRESET_VCF) {
			return 8; // VCF files use the INFO field for ends of structural variants
		}
		int max = tabix.config.seqCol;
		max = Math.max(max, tabix.config.beginCol);
		max = Math.max(max, tabix.config.endCol);
		return max;
	}
	
	public void writeIndex() throws IOException {
		// TODO After writing the index, shouldn't allow addition of more rows
		finishPrevChromosome(tidCurr);
		BlockCompressedOutputStream indexOutput = new BlockCompressedOutputStream(fileName + ".gz.tbi");
		tabix.saveIndex(indexOutput);
		indexOutput.close();
		bcos.close();
	}

	public static void main(String args[]) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(args[0]));
		TabixCompressorAndWriter tcaw = new TabixCompressorAndWriter(args[0], TabixConfig.VCF);
		String line = null;
		while ((line = br.readLine()) != null) {
			tcaw.addLine(line);
		}
		tcaw.writeIndex();
		br.close();
	}

}
