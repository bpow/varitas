package org.drpowell.grandannotator;

import java.io.*;
import java.util.ArrayList;
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
	private StringBuffer metaHeaders = new StringBuffer();
	private ArrayList<VCFMeta> parsedHeaders = new ArrayList<VCFMeta>();
	private Map<String, VCFMeta> infoHeaders = new LinkedHashMap<String, VCFMeta>();
	private String colHeaders;
	private String fileName;
	private BufferedReader reader;
	private String [] samples;
	private boolean alreadyProvidedIterator = false;
	private String nextLine;
	
	public String getMetaHeaders() {
		return metaHeaders.toString();
	}
	
	public String getColHeaders() {
		return colHeaders;
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
				return;
			} else {
				String error = "Error parsing header: \n" + line;
				if (fileName != null) {
					error += "\n in file: " + fileName;
				}
				throw new RuntimeException(error); 
			}
		}
		readNext();
	}
	
	private VCFMeta addMeta(String line) {
		// FIXME - use system eol character
		metaHeaders.append(line).append('\n');
		VCFMeta meta = VCFMeta.fromLine(line);
		parsedHeaders.add(meta);
		if (meta.getMetaKey().equals("INFO")) {
			infoHeaders.put(meta.getValue("ID"), meta);
		}
		return meta;
	}
	
	private void parseColHeader(String line) {
		if (!line.startsWith(VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS)) {
			throw new RuntimeException("Problem reading VCF file\nExpected: " +
					VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS + "Found:    " + line);
		}
		colHeaders = line;
		String [] headers = line.split("\t");
		int numFixed = VCFFixedColumns.values().length;
		samples = new String[headers.length - numFixed];
		for (int i = numFixed; i < headers.length; i++) {
			samples[i-numFixed] = headers[i];
		}
	}

	@Override
	public Iterator<VCFVariant> iterator() {
		if (alreadyProvidedIterator) {
			throw new UnsupportedOperationException("Tried to access iterator for VCFParser twice");
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
		} catch (IOException ex) {
			Logger.getLogger(VCFParser.class.getName()).log(Level.SEVERE, null, ex);
		}
		return null;
	}

}
