/**
 * 
 */
package org.drpowell.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import net.sf.picard.util.TabbedInputParser;
import net.sf.samtools.util.RuntimeIOException;
import net.sf.samtools.util.SortingCollection.Codec;
import net.sf.samtools.util.StringUtil;

/**
 * This is a simple codec for tab-delimited files.
 * 
 * @author Bradford Powell
 *
 */
public class TabbedCodec implements Codec<String[]> {
	private OutputStreamWriter output;
	private TabbedInputParser tip;
	private static final String EOL = System.getProperty("line.separator");

	// there's no state other than the input and output, so just return a new TabbedCodec object
	@Override
	public Codec<String []> clone() { return new TabbedCodec(); }

	@Override
	public void setOutputStream(OutputStream os) {
		output = new OutputStreamWriter(os);
	}
	
	@Override
	public void setInputStream(InputStream is) {
		tip = new TabbedInputParser(false, is);
	}

	
	/**
	 *  Although not stated in the documentation for net.sf.samtools.util.SortingCollection.codec, it is expected
	 * by SortingCollection that all data will be flushed to the client OutputStream at the time that encode()
	 * is finished. Unfortunately, calling flush() on the client OutputStream (as done by this method) also
	 * ends up passing that flush along, eliminating the benefit of buffering,
	 *
	 * @see net.sf.samtools.util.SortingCollection.Codec#encode(java.lang.Object)
	 */
	@Override
	public void encode(String[] values) {
		try {
			output.write(StringUtil.join("\t", values));
			output.write(EOL);
			output.flush();
		} catch (IOException e) {
			throw new RuntimeIOException("Unable to write tab-delimited file: ", e);
		}
	}

	@Override
	public String[] decode() {
		if (!tip.hasNext()) return null;
		return tip.next();
	}
	
}
