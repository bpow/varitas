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
public class Grouper<K,V> implements Iterator<Collection <V>> {
	private final Iterator<V> delegate;
	private final KeyFunction<K,V> keyF;
	private V nextItem = null;
	private K prevKey;
	private K nextKey;
	
	public Grouper(Iterator<V> client, KeyFunction<K,V> keyFunction) {
		delegate = client;
		keyF = keyFunction;
		advance();
	}
	
	private K advance() {
		if (delegate.hasNext()) {
			nextItem = delegate.next();
			nextKey = keyF.apply(nextItem);
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
		while (nextItem != null && prevKey.equals(keyF.apply(nextItem))) {
			out.add(nextItem);
			advance();
		}
		return out;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	/**
	 * This interface provides a single method to generate a key for the use of grouping objects, so
	 * it is basically a closure. The object returned will be compared using <pre>.equals()</pre>
	 */
	public interface KeyFunction<K,V> {
		public abstract K apply(V value);
	}
	
	/**
	 * A simple key-generator that returns the string value of its argument.
	 */
	public static class ToStringKey<V> implements KeyFunction<String, V> {
		@Override
		public String apply(V value) {
			return value.toString();
		}
	}

	public static void main(String argv[]) {
		String [] tests = {"one", "one", "one", "two", "three", "three", "a", "b"};
		Grouper<String, String> g = new Grouper<String, String>(Arrays.asList(tests).iterator(), new Grouper.ToStringKey<String>());
		while (g.hasNext()) {
			System.out.println((Iterable<String>) g.next());
		}
	}

	
}
