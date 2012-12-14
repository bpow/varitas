package org.drpowell.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class FilteringIterator<E> extends AbstractPeekableIterator<E> {
	private final Iterator<E> delegate;
	
	public abstract boolean filter(E element);
	
	public FilteringIterator(Iterator<E> client) {
		delegate = client;
	}

	protected E computeNext() {
		while (delegate.hasNext()) {
			E nextValue = delegate.next();
			if (filter(nextValue)) return nextValue;
		}
		return endOfData();
	}

}
