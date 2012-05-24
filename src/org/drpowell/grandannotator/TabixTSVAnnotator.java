package org.drpowell.grandannotator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class TabixTSVAnnotator extends Annotator {
	private final TabixReader tabix;
	private final Map<Integer, String> fieldMap = new LinkedHashMap<Integer, String>();
	private static Logger logger = Logger.getLogger(TabixTSVAnnotator.class.getCanonicalName());
	
	public TabixTSVAnnotator(final TabixReader reader, String columns) {
		tabix = reader;
		String [] splitColumns = columns.split(",");
		for (String column : splitColumns) {
			int eq = column.indexOf("=");
			if (eq >= 0) {
				fieldMap.put(Integer.valueOf(column.substring(0, eq))-1, column.substring(eq+1));
			} else {
				fieldMap.put(Integer.valueOf(column)-1, "col" + column);
			}
		}
	}
	
	@Override
	public VCFVariant annotate(VCFVariant variant) {
		String chromosome = variant.getSequence();
		Integer tid = tabix.getIdForChromosome(prefix + chromosome);
		if (tid == null) {
			logger.info(prefix + chromosome + " is not found in file " + tabix.filename);
			return variant;
		}
		String [] row;
		String ref = variant.getRef();
		String alt = variant.getAlt();
		Map<String, Object> info = variant.getInfo();
		// when using this query form, tabix expects space-based (0-based) coordinates
		TabixReader.Iterator iterator = tabix.query(tid, variant.getStart()-1, variant.getEnd());
		try {
			while ((row = iterator.next()) != null) {
				// TODO - should we check start/stop to make sure exact? probably...
				if ((refColumn < 0 || row[refColumn].equals(ref)) &&
					(altColumn < 0 || row[altColumn].equals(alt))) {
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
		return variant;
		
	}

	@Override
	public Iterable<String> infoLines() {
		ArrayList<String> infos = new ArrayList<String>();
		for (Map.Entry<Integer, String> entry : fieldMap.entrySet()) {
			LinkedHashMap<String, String> infoValues = new LinkedHashMap<String, String>();
			infoValues.put("ID", entry.getValue());
			infoValues.put("Number", "1");
			infoValues.put("Type", "String");
			infoValues.put("Description", "\"Column " + Integer.toString(entry.getKey() + 1) + " from " + tabix.filename + "\"");
			// FIXME - can do better with the descriptions!
			infos.add(new VCFMeta("INFO", infoValues).toString());
		}
		return infos;
	}
	
	@Override
	public String toString() {
		return "TabixTSVannotator: " + tabix.filename;
	}
	
}
