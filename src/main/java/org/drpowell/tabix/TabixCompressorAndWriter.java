package org.drpowell.tabix;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import net.sf.samtools.LinearIndex;
import net.sf.samtools.util.BlockCompressedFilePointerUtil;
import net.sf.samtools.util.BlockCompressedOutputStream;
import net.sf.samtools.util.StringUtil;

public class TabixCompressorAndWriter {
	private class BIndex extends LinkedHashMap<Integer, List<Tabix.Pair64Unsigned>> {
		public BIndex(int capacity, float loadFactor) {
			super(capacity, loadFactor);
		}
		public List<Tabix.Pair64Unsigned> getWithNew(int i) {
			List<Tabix.Pair64Unsigned> out = get(i);
			if (out == null) {
				out = new ArrayList<Tabix.Pair64Unsigned>(); // TODO- presize or change to LinkedList
			}
			put(i, out);
			return out;
		}
	}
	
	private class LIndex extends AbstractList<Long> {
		private long[] index;
		int size = 0;
		public LIndex() {
			index = new long[LinearIndex.MAX_LINEAR_INDEX_SIZE];
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
		public LIndex(LIndex old) {
			size = old.size();
			index = new long[size];
			System.arraycopy(old.index, 0, index, 0, size);
		}
		public void clear() {
			Arrays.fill(index, 0L);
			size = 0;
		}
	}
	
	private final BlockCompressedOutputStream bcos;
	private final String fileName;
	private final Tabix tabix;
	private final int maxColOfInterest;
	private int tidCurr = -1;
	private BIndex currBinningIndex = new BIndex(Tabix.MAX_BIN, 1.0f);
	private LIndex currLinearIndex = new LIndex();
	
	public TabixCompressorAndWriter(String fileName, Tabix tabix) {
		this.fileName = fileName;
		bcos = new BlockCompressedOutputStream(fileName + ".gz");
		this.tabix = tabix;
		maxColOfInterest = calcMaxCol();
	}

	public void addLine(String line) throws IOException {
		line += "\n";
		if (line.startsWith(tabix.comment)) {
			bcos.write(line.getBytes());
			return;
		}
		String [] row = new String[maxColOfInterest];
		StringUtil.split(line, row, '\t');
		Tabix.GenomicInterval intv = tabix.getInterval(row);
		if (intv.tid != tidCurr && tidCurr >= 0) {
			finishPrevChromosome(tidCurr);
		}
		tidCurr = intv.tid;
		final long startOffset = bcos.getFilePointer();
		bcos.write(line.getBytes());
		final long endOffset = bcos.getFilePointer();
		Tabix.Pair64Unsigned chunk = new Tabix.Pair64Unsigned(startOffset, endOffset);
		
		// process binning index
		List<Tabix.Pair64Unsigned> bin = currBinningIndex.getWithNew(intv.bin);
		// check for overlapping chunks
		if (bin.isEmpty()) {
			bin.add(chunk);
		} else {
			Tabix.Pair64Unsigned lastChunk = bin.get(bin.size()-1);
			if (BlockCompressedFilePointerUtil.areInSameOrAdjacentBlocks(lastChunk.v, chunk.u)) {
				bin.set(bin.size()-1, new Tabix.Pair64Unsigned(lastChunk.u, chunk.v)); // coalesce
			} else {
				bin.add(chunk);
			}
		}
		
		// process linear index
		// TODO - do I need to check for off-by-one changes?
		int startWindow = LinearIndex.convertToLinearIndexOffset(intv.beg);
		int endWindow = LinearIndex.convertToLinearIndexOffset(intv.end);
		for (int win = startWindow; win <= endWindow; win++) {
			if (currLinearIndex.getPrimitive(win) == 0 || chunk.u < currLinearIndex.getPrimitive(win)) {
				currLinearIndex.setPrimitive(win, chunk.u);
			}
		}
	}
	
	private void finishPrevChromosome(int tidPrev) {
		if (currBinningIndex.isEmpty()) return; // saw nothing for this reference
		
		// make things as compact as possible...
		tabix.binningIndex.set(tidPrev, currBinningIndex);
		currBinningIndex = new BIndex(Tabix.MAX_BIN, 1.0f);
		// TODO - compact binning index
		
		tabix.linearIndex.set(tidPrev, new LIndex(currLinearIndex));
		currLinearIndex.clear();
	}
	
	private final int calcMaxCol() {
		int max = tabix.seqColumn;
		max = Math.max(max, tabix.startColumn);
		max = Math.max(max, tabix.endColumn);
		return max+8;
	}
	
	public void writeIndex() throws IOException {
		// TODO After writing the index, shouldn't allow addition of more rows
		finishPrevChromosome(tidCurr);
		tabix.saveIndex(new BlockCompressedOutputStream(fileName + ".gz.tbi"));
		bcos.close();
	}

	public static void main(String args[]) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(args[0]));
		TabixCompressorAndWriter tcaw = new TabixCompressorAndWriter(args[0], Tabix.VCF_CONF);
		String line = null;
		while ((line = br.readLine()) != null) {
			tcaw.addLine(line);
		}
		tcaw.writeIndex();
	}

}
