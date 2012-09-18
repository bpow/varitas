package org.drpowell.tabix;

import java.util.ArrayList;

class GenomicInterval {
	private final int begin, end, sequenceId;
	private int bin = -1;

	public GenomicInterval(int begin, int end, int sequenceId) {
		this.begin = begin; this.end = end; this.sequenceId = sequenceId;
	}
	
	public int getBegin() { return begin; }
	public int getEnd() { return end; }
	public int getSequenceId() { return sequenceId; }
	
	public int getBin() {
		if (bin == -1) {
			bin = reg2bin(begin, end);
		}
		return bin;
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