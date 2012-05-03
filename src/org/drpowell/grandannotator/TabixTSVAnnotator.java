package org.drpowell.grandannotator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class TabixTSVAnnotator implements Annotator {
	private final TabixReader tabix;
	private final Map<Integer, String> fieldMap = new LinkedHashMap<Integer, String>();
	private String prefix = "";
	private int refColumn = -1;
	private int altColumn = -1;
	
	public TabixTSVAnnotator(final TabixReader reader, String columns) {
		tabix = reader;
		String [] splitColumns = columns.split(",");
		for (String column : splitColumns) {
			int eq = column.indexOf("=");
			if (eq >= 0) {
				fieldMap.put(Integer.valueOf(column.substring(0, eq)), column.substring(eq+1));
			} else {
				fieldMap.put(Integer.valueOf(column), "col" + column);
			}
		}
	}
	
	@Override
	public Map<String, Object> annotate(VCFVariant var) {
		return annotate(var.getSequence(), var.getStart(), var.getEnd(), var.getRef(), var.getAlt(), var.getInfo());
	}

	@Override
	public Map<String, Object> annotate(String chromosome, int start, int end,
			String ref, String alt, Map<String, Object> info) {
		Integer tid = tabix.getIdForChromosome(prefix + chromosome);
		if (tid == null) {
			// may want to log this...
			return info;
		}
		String [] row;
		// when using this query form, tabix expects space-based (0-based) coordinates
		TabixReader.Iterator iterator = tabix.query(tid, start-1, end);
		try {
			while ((row = iterator.next()) != null) {
				// TODO - should we check start/stop to make sure exact? probably...
				if ((refColumn < 0 || row[refColumn].equals(ref)) && (altColumn < 0 || row[altColumn].equals(alt))) {
					// we have a match!
					for (Map.Entry<Integer, String> entry: fieldMap.entrySet()) {
						String value = row[entry.getKey()];
						if (value != "") {
							info.put(entry.getValue(), row[entry.getKey()]);
						}
					}
				}
			}
		} catch (IOException ioe) {
			System.err.println(ioe);
		}
		return info;
		
	}

	/**
	 * Provide a column number which is the "reference" call at a locus, for checking in the annotation process.
	 * Coordinates are 1-based (i.e. the 1st column is column #1.
	 * 
	 * If the argument altColumn is less than or equal to 0, then no checking will be performed.
	 * 
	 * @param altColumn
	 * @return this, so you can chain calls
	 */
	public TabixTSVAnnotator checkRef(int refColumn) {
		this.refColumn = refColumn-1;
		return this;
	}

	/**
	 * Provide a column number which is the "alternate" call at a locus, for checking in the annotation process.
	 * Coordinates are 1-based (i.e. the 1st column is column #1.
	 * 
	 * If the argument altColumn is less than or equal to 0, then no checking will be performed.
	 * 
	 * @param altColumn
	 * @return this, so you can chain calls
	 */
	public TabixTSVAnnotator checkAlt(int altColumn) {
		this.altColumn = altColumn-1;
		return this;
	}
	
}
