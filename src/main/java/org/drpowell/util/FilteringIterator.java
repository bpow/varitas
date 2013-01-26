package org.drpowell.util;

import java.util.Iterator;

public abstract class FilteringIterator<E> extends AbstractPeekableIterator<E> {
	private final Iterator<E> delegate;
	
	public abstract E filter(E element);
	
	public FilteringIterator(Iterator<E> client) {
		delegate = client;
	}

	protected E computeNext() {
		while (delegate.hasNext()) {
			E nextValue = delegate.next();
			if (filter(nextValue) != null) return nextValue;
		}
		return endOfData();
	}

}
