package org.drpowell.tabix;

import java.util.AbstractList;

/**
 * This class is a performance optimization to get around behavior of String.split().
 * 
 * String.split exists in many incarnations, but however you slice it (or "split it"),
 * the performance of splitting tab-delimited items is important for processing
 * tabix records. Even with a fast path in openjdk 7, String.split doesn't perform as
 * well as this code (for my test sets, as of early 2013).
 * 
 * So, instead I just keep a list of delimiter locations in the string and return
 * substrings when List.get() is called.
 * 
 * Because a new String object is created with every #get(), it would be best to use
 * Collections.copy or #toArray() to make a copy of this list if you are going to
 * use items from the list multiple times.
 * 
 * @author Bradford Powell
 *
 */
public class DelimitedString extends AbstractList<String> {
	public final char delimiter;
	public final String concatenated;
	private final int [] delimiterLocations;

	public DelimitedString(String s, char delimiter) {
		concatenated = s;
		this.delimiter = delimiter;
		delimiterLocations = findDelimiters(s, delimiter);
	}
	
	private int [] findDelimiters(String s, char delim) {
		int [] temp = new int[s.length()+1];
		int currDelim = 0;
		temp[currDelim++] = -1;
		int off = 0;
		while (off < s.length()) {
			off = s.indexOf(delim, off);
			if (off < 0) {
				break;
			}
			temp[currDelim++] = off;
			off++;
		}
		temp[currDelim++] = s.length();
		int [] out = new int[currDelim];
		System.arraycopy(temp, 0, out, 0, currDelim);
		return out;
	}

	@Override
	public String get(int index) {
		return concatenated.substring(delimiterLocations[index]+1, delimiterLocations[index+1]);
	}

	@Override
	public int size() {
		return delimiterLocations.length - 1;
	}

}
