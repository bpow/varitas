package org.drpowell.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LineIterator extends AbstractPeekableIterator<String> {
	public final BufferedReader reader;
	
	public boolean handleException(Exception e) {
		Logger.getGlobal().log(Level.WARNING, e.toString());
		return true;
	}
	
	public LineIterator(BufferedReader reader) {
		this.reader = reader;
	}

	@Override
	protected String computeNext() {
		String next = null;
		try {
			next = reader.readLine();
		} catch (IOException e) {
			if (!handleException(e)) {
				return computeNext();
			}
		}
		return next == null ? endOfData() : next;
	}

}
