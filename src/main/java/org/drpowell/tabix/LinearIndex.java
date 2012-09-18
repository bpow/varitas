package org.drpowell.tabix;

import java.util.AbstractList;
import java.util.Arrays;

import net.sf.samtools.AbstractBAMFileIndex;

class LinearIndex extends AbstractList<Long> {
	public static final int TBX_LIDX_SHIFT = 14;
	protected long[] index;
	int size = 0;
	public LinearIndex() {
		index = new long[AbstractBAMFileIndex.MAX_LINEAR_INDEX_SIZE];
	}
	
	public LinearIndex(int n_linear) {
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
	
	public void clear() {
		Arrays.fill(index, 0L);
		size = 0;
	}
	
	protected void fillZeros() {
		// FIXME - could be done as part of constructor from pre-filled index
        long lastNonZeroOffset = 0;
        for (int i = 0; i < index.length; i++) {
            if (index[i] == 0) {
                index[i] = lastNonZeroOffset; // not necessary, but C (samtools index) does this
                // note, if you remove the above line BAMIndexWriterTest.compareTextual and compareBinary will have to change
            } else {
                lastNonZeroOffset = index[i];
            }
        }
	}
	
	public LinearIndex getCompacted() {
		fillZeros();
		return new UnmodifiableLinearIndex(this);
	}

    public static int convertToLinearIndexOffset(final int contigPos) {
        final int indexPos = (contigPos <= 0) ? 0 : contigPos-1;
        return indexPos >> TBX_LIDX_SHIFT;
    }
    
    /**
     * Gets the minimum offset of any alignment start appearing in this index, according to the linear index. 
     * @param startPos Starting position for this query.
     * @return The minimum offset, in chunk format, of any read appearing in this position.
     */
    public long getMinimumOffset(final int startPos) {
        final int start = (startPos <= 0) ? 0 : startPos-1;
        final int regionLinearBin = start >> TBX_LIDX_SHIFT;
        // System.out.println("# regionLinearBin: " + regionLinearBin);
        if (regionLinearBin >= size()) { return getPrimitive(size - 1); }
        return getPrimitive(regionLinearBin);
    }
    
    class UnmodifiableLinearIndex extends LinearIndex {
    	public UnmodifiableLinearIndex(LinearIndex orig) {
    		size = orig.size();
    		index = new long[size];
    		System.arraycopy(orig.index, 0, index, 0, size);
    	}
    	public void clear() { throw new IllegalStateException("This index cannot be modified now."); }
    	public long setPrimitive(int i, long l) { throw new IllegalStateException("This index cannot be modified now."); }
    }


}