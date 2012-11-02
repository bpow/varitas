package org.drpowell.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class FilteringIterator<E> implements Iterator<E> {
	private E nextValue;
	private final Iterator<E> delegate;
	
	public abstract boolean filter(E element);
	
	public FilteringIterator(Iterator<E> client) {
		delegate = client;
		advance();
	}
	
	private E advance() {
		E curr = nextValue;
		nextValue = null;
		while (delegate.hasNext()) {
			nextValue = delegate.next();
			if (filter(nextValue)) break;
			nextValue = null;
		}
		return curr;
	}

	@Override
	public boolean hasNext() {
		return nextValue != null;
	}

	@Override
	public E next() {
		E res = advance();
		if (res == null) throw new NoSuchElementException();
		return res;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}
