package org.drpowell.vcf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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

	private StringBuffer metaHeaders = new StringBuffer();
	private ArrayList<VCFMeta> parsedHeaders = new ArrayList<VCFMeta>();
	private Map<String, VCFMeta> infoHeaders = new LinkedHashMap<String, VCFMeta>();
	private Map<String, VCFMeta> formatHeaders = new LinkedHashMap<String, VCFMeta>();
	private String colHeaders;
	private String fileName;
	private BufferedReader reader;
	private String [] samples;
	private boolean alreadyProvidedIterator = false;
	private String nextLine;
	
	public String getMetaHeaders() {
		return metaHeaders.toString();
	}
	
	public Map<String, VCFMeta> infos() {
		return Collections.unmodifiableMap(infoHeaders);
	}

	public Map<String, VCFMeta> formats() {
		return Collections.unmodifiableMap(formatHeaders);
	}

	public String getColHeaderLine() {
		return colHeaders;
	}
	
	public String [] samples() {
		return samples;
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
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("##")) {
				addMeta(line);
			} else if (line.startsWith("#CHROM")) {
				parseColHeader(line);
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
			return new VCFMeta(line, (LinkedHashMap<String, String>) null);
		}
		String metaKey = line.substring(0, eq);
		String remainder = line.substring(eq+1);
		if (remainder.startsWith("<") && remainder.endsWith(">")) {
			return new VCFMeta(metaKey, parseMultipleMetaValues(remainder.substring(1, remainder.length()-1)));
		}
		return new VCFMeta(metaKey, remainder);
	}
	
	// FIXME - this belongs in a VCFVariantCollection, if anywhere...
	public VCFMeta addMeta(String line) {
		// FIXME - use system eol character
		metaHeaders.append(line).append('\n');
		VCFMeta meta = parseVCFMeta(line);
		parsedHeaders.add(meta);
		if ("INFO".equals(meta.getMetaKey())) {
			infoHeaders.put(meta.getValue("ID"), meta);
		}
		if ("FORMAT".equals(meta.getMetaKey())) {
			formatHeaders.put(meta.getValue("ID"), meta);
		}
		return meta;
	}
	
	private void parseColHeader(String line) {
		if (!line.startsWith(VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS)) {
			throw new RuntimeException("Problem reading VCF file\nExpected: " +
					VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS + "Found:    " + line);
		}
		colHeaders = line;
		String [] headers = line.split("\t", -1);
		int numFixed = VCFFixedColumns.values().length;
		samples = new String[headers.length - numFixed];
		for (int i = numFixed; i < headers.length; i++) {
			samples[i-numFixed] = headers[i];
		}
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
