package org.drpowell.util;

import htsjdk.samtools.util.CloseableIterator;

public abstract class FilteringIterator<E> extends AbstractPeekableIterator<E> {
	protected final CloseableIterator<E> delegate;
	
	public abstract E filter(E element);
	
	public FilteringIterator(CloseableIterator<E> client) {
		delegate = client;
	}

	protected E computeNext() {
		while (delegate.hasNext()) {
			E nextValue = delegate.next();
			if (filter(nextValue) != null) return nextValue;
		}
		return endOfData();
	}

	public void close() {
		delegate.close();
	}

}
