package org.drpowell.varitas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.drpowell.tabix.TabixReader;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFVariant;

public class TabixTSVAnnotator extends Annotator {
	private final TabixReader tabix;
	private final Map<Integer, String> fieldMap = new LinkedHashMap<Integer, String>();
	private final Map<Integer, String> descriptionMap = new LinkedHashMap<Integer, String>();
	private static Logger logger = Logger.getLogger(TabixTSVAnnotator.class.getCanonicalName());
	private boolean hasHeader = false;
	
	public TabixTSVAnnotator(final TabixReader reader, String columns) {
		tabix = reader;
		String [] splitColumns = columns.split(",");
		Integer inputColumnNumber;
		String infoKey = null;
		String description = null;
		for (String column : splitColumns) {
			int eq = column.indexOf('=');
			if (eq >= 0) {
				inputColumnNumber = Integer.valueOf(column.substring(0, eq))-1;
				column = column.substring(eq+1);
				int colon = column.indexOf(':');
				if (colon >= 0) {
					infoKey = column.substring(0, colon);
					description = '"' + column.substring(colon+1) + '"';
				} else {
					infoKey = column;
				}
			} else {
				inputColumnNumber = Integer.valueOf(column)-1;
				infoKey = "col" + column;
			}
			fieldMap.put(inputColumnNumber, infoKey);
			descriptionMap.put(inputColumnNumber, description);
		}
	}
	
	public TabixTSVAnnotator useHeader(boolean useHeader) {
		hasHeader = useHeader;
		return this;
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
		// when using this query form, tabix expects space-based (0-based) coordinates
		Iterator<String []> iterator = tabix.getIndex().query(tid, variant.getStart()-1, variant.getEnd());
		while ((row = iterator.next()) != null) {
			// TODO - should we check start/stop to make sure exact? probably...
			if ((refColumn < 0 || row[refColumn].equals(ref)) &&
				(altColumn < 0 || row[altColumn].equals(alt))) {
				// we have a match!
				for (Map.Entry<Integer, String> entry: fieldMap.entrySet()) {
					String value = row[entry.getKey()];
					if (! ("".equals(value) || ".".equals(value)) ) {
						// FIXME -- "." is frequently used to represent missing data, but consider whether I should pass it along
						variant.putInfo(entry.getValue(), value);
					}
				}
			}
		}
		return variant;
		
	}

	@Override
	public Iterable<String> infoLines() {
		ArrayList<String> infos = new ArrayList<String>();
		List<String> headers = null;
		if (hasHeader) {
			try {
				headers = tabix.readHeaders();
				if (!headers.isEmpty()) {
					headers = Arrays.asList(headers.get(0).split("\\t",-1));
				}
			} catch (IOException ioe) {
				logger.warning("problem reading from headers for " + tabix.filename + "\n" + ioe);
			}
		}
		for (Map.Entry<Integer, String> entry : fieldMap.entrySet()) {
			LinkedHashMap<String, String> infoValues = new LinkedHashMap<String, String>();
			infoValues.put("ID", entry.getValue());
			infoValues.put("Number", "1");
			infoValues.put("Type", "String");
			int colIndex = entry.getKey();
			String description = descriptionMap.get(colIndex);
			if (description != null) {
				infoValues.put("Description", description);
			} else if (headers != null && headers.size() >= colIndex) {
				infoValues.put("Description", "\"" + headers.get(colIndex) + ", column " + Integer.toString(colIndex + 1) + " from " + tabix.filename + "\"");
			} else {
				infoValues.put("Description", "\"Column " + Integer.toString(colIndex + 1) + " from " + tabix.filename + "\"");
			}
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
