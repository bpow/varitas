package org.drpowell.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TeeOutputStream extends FilterOutputStream {
	private final OutputStream copy;

	public TeeOutputStream(OutputStream out, OutputStream copy) {
		super(out);
		this.copy = copy;
	}

	@Override
	public void write(int b) throws IOException {
		super.write(b);
		copy.write(b);
	}

	@Override
	public void flush() throws IOException {
		super.flush();
		copy.flush();
	}

	@Override
	public void close() throws IOException {
		super.close();
		copy.close();
	}

}
