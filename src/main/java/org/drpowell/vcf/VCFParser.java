package org.drpowell.vcf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bpow
 */
public class VCFParser implements Iterable<VCFVariant>, Iterator<VCFVariant> {
	
	public enum VCFFixedColumns {
		CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO, FORMAT;
		public static final String VCF_FIXED_COLUMN_HEADERS =
				"#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT";
		public static final int SIZE = values().length;
	}

	private VCFHeaders headers;
	private String fileName;
	private BufferedReader reader;
	private String [] samples;
	private boolean alreadyProvidedIterator = false;
	private String nextLine;
	
	public VCFHeaders getHeaders() {
		return headers;
	}
	
	public VCFParser(BufferedReader reader) throws IOException {
		this.reader = reader;
		parseHeaders();
	}
	
	public VCFParser(String file) throws IOException {
		fileName = file;
		reader = new BufferedReader(new FileReader(file));
		parseHeaders();
	}
	
	private void parseHeaders() throws IOException {
		ArrayList<VCFMeta> parsedHeaders = new ArrayList<VCFMeta>();

		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("##")) {
				parsedHeaders.add(parseVCFMeta(line));
			} else if (line.startsWith("#CHROM")) {
				if (!line.startsWith(VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS)) {
					throw new RuntimeException("Problem reading VCF file\nExpected: " +
							VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS + "Found:    " + line);
				}
				String [] colheaders = line.split("\t", -1);
				int numFixed = VCFFixedColumns.values().length;
				samples = new String[colheaders.length - numFixed];
				for (int i = numFixed; i < colheaders.length; i++) {
					samples[i-numFixed] = colheaders[i];
				}
				headers = new VCFHeaders(parsedHeaders, samples);
				readNext();
				return;
			} else {
				String error = "Error parsing header: \n" + line;
				if (fileName != null) {
					error += "\n in file: " + fileName;
				}
				throw new RuntimeException(error); 
			}
		}
	}
	
	private static LinkedHashMap<String, String> parseMultipleMetaValues(String multiValues) {
		String [] keyvalues = multiValues.split(",");
		LinkedHashMap<String, String> result = new LinkedHashMap<String, String>(keyvalues.length * 5 % 4);
		String prev = null;
		for (String s : keyvalues) {
			if (prev != null) {
				s = prev + "," + s;
				prev = null;
			}
			if ((s.indexOf('"') >= 0 ) && ((s.endsWith("\\\"") || !s.endsWith("\"")))) {
				prev = s;
			} else {
				int eq = s.indexOf("=");
				if (eq > 0) {
					result.put(s.substring(0, eq), s.substring(eq+1));
				} else {
					result.put(s, "");
				}
			}
		}
		return result;
	}
	
	public static VCFMeta parseVCFMeta(String line) {
		if (line.startsWith("##")) {
			line = line.substring(2);
		}
		int eq = line.indexOf("=");
		if (eq <= 0) {
			return new VCFMeta(line);
		}
		String metaKey = line.substring(0, eq);
		String remainder = line.substring(eq+1);
		if (remainder.startsWith("<") && remainder.endsWith(">")) {
			return new VCFMeta(metaKey, parseMultipleMetaValues(remainder.substring(1, remainder.length()-1)));
		}
		return new VCFMeta(metaKey, remainder);
	}

	@Override
	public Iterator<VCFVariant> iterator() {
		if (alreadyProvidedIterator) {
			throw new IllegalStateException("Tried to access iterator for VCFParser twice");
		} else {
			alreadyProvidedIterator = true;
		}
		return this;
	}
	
	// now for the iterator implementation...
	
	@Override
	public boolean hasNext() {
		return nextLine != null;
	}

	@Override
	public VCFVariant next() {
		// FIXME - should we also set alreadyProvidedIterator here?
		VCFVariant v = new VCFVariant(nextLine);
		nextLine = readNext();
		return v;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("Read-only iterator");
	}

	private String readNext() {
		try {
			nextLine = reader.readLine();
			if (nextLine == null) {
				reader.close();
			}
			return nextLine;
		} catch (IOException ex) {
			Logger.getLogger(VCFParser.class.getName()).log(Level.SEVERE, null, ex);
		}
		return null;
	}

}
