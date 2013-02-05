package org.drpowell.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator that groups sub-iterators based on some key, like python's itertools.group_by
 * 
 * As an example, if you have an iterator of String []'s, and want to group them by the string
 * value in the second element of each array:
 * 
 * <pre>
 * Iterator<String []> it = ...
 * Grouper<String []> g = new Grouper<String []>(it, new KeyFunction<String []>() {
 *   @Override
 *   public Object apply(String [] value) {
 *     return value[1];
 *   }
 * });</pre>
 * 
 * Then, each call to g.next() will return an Iterable<String []> of grouped rows.
 * 
 * For now, nulls are not permitted in the underlying iterator (null is used as a marker of 
 * the end of iteration).
 * 
 * @author Bradford Powell
 *
 */
public abstract class Grouper<K,V> implements Iterator<Collection <V>> {
	private Iterator<V> delegate;
	private V nextItem = null;
	private K prevKey;
	private K nextKey;
	
	public Grouper() {
		delegate = null;
	}
	
	public Grouper(Iterator<V> client) {
		delegate = client;
		advance();
	}
	
	public Grouper<K,V> setDelegate(Iterator<V> delegate) {
		this.delegate = delegate;
		advance();
		return this;
	}
	
	/**
	 * Given a value, return the grouping key for that datum
	 */
	public abstract K keyForValue(V value);
	
	private K advance() {
		if (delegate.hasNext()) {
			nextItem = delegate.next();
			nextKey = keyForValue(nextItem);
		} else {
			nextItem = null;
			nextKey = null;
		}
		return nextKey;
	}

	@Override
	public boolean hasNext() {
		return nextItem != null;
	}

	@Override
	public Collection<V> next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		// TODO - implementation detail: ArrayList or LinkedList?
		ArrayList<V> out = new ArrayList<V>();
		prevKey = nextKey;
		if (nextKey == null) {
			// nulls go in their own groups (a null is not equal to another null)
			prevKey = nextKey;
			out.add(nextItem);
			advance();
			return out;
		}
		while (nextItem != null && prevKey.equals(keyForValue(nextItem))) {
			out.add(nextItem);
			advance();
		}
		return out;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	public static class StringGrouper<V> extends Grouper<String, V> {
		@Override
		public String keyForValue(V value) {
			return value.toString();
		}
	}
	
	public K getLastGroupedKey() {
		return prevKey;
	}
	
	public static void main(String argv[]) {
		String [] tests = {"one", "one", "one", "two", "three", "three", "a", "b"};
		Grouper<String, String> g = new StringGrouper<String>().setDelegate(Arrays.asList(tests).iterator());
		while (g.hasNext()) {
			System.out.println((Iterable<String>) g.next());
		}
	}

	
}
