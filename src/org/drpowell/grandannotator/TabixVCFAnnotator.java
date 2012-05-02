package org.drpowell.grandannotator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;

public class TabixVCFAnnotator implements Annotator {
	private final TabixReader tabix;
	private final Map<String, String> fieldMap = new HashMap<String, String>();
	private String prefix = ""; // can be "chr" if we need to add a prefix for query purposes
	private boolean requirePass;

	public static final String stringJoin(String delimiter, String[] strings) {
		StringBuilder sb = new StringBuilder();
		for (String string : strings) {
			sb.append(string).append(delimiter);
		}
		return sb.substring(0, sb.length() - delimiter.length());
	}
	
	public TabixVCFAnnotator(final TabixReader reader, final Map<String, String> fields) {
		tabix = reader;
		fieldMap.putAll(fields);
	}
	
	public TabixVCFAnnotator(final TabixReader reader, String fieldString) {
		this.tabix = reader;
		String [] fields = fieldString.split(",");
		for (String field : fields) {
			int eq = field.indexOf("=");
			if (eq < 0) {
				fieldMap.put(field, field);
			} else {
				fieldMap.put(field.substring(0, eq), field.substring(eq+1));
			}
		}
	}
	
	@Override
	public ConcurrentMap<String, Object> annotate(VCFVariant var) {
		return annotate(var.getSequence(), var.getStart(), var.getEnd(), var.getRef(), var.getAlt(), var.getInfo());
	}
	
	@Override
	public ConcurrentMap<String, Object> annotate(final String chromosome, final int start, final int end, final String ref, final String alt, ConcurrentMap<String, Object> info) {
		Integer tid = tabix.getIdForChromosome(prefix + chromosome);
		if (tid == null) {
			// may want to log this...
			return info;
		}
		String [] resultRow;
		// when using this query form, tabix expects space-based (0-based) coordinates
		TabixReader.Iterator iterator = tabix.query(tid, start-1, end);
		try {
			while ((resultRow = iterator.next()) != null) {
				VCFVariant target = new VCFVariant(resultRow);
				// check on position (1), ref (3) and alt (4)
				if (target.getStart() == start && target.getRef().equals(ref) && target.getAlt().equals(alt)) {
					if (requirePass && !target.getFilter().equals("PASS")) {
						continue;
					}
					// found a match!
					Map<String, Object> targetInfo = target.getInfo();
					for (Entry<String, String> e: fieldMap.entrySet()) {
						if (targetInfo.containsKey(e.getKey())) {
							// FIXME- should check to prevent duplicates being overwritten
							info.put(e.getValue(), targetInfo.get(e.getKey()));
						}
					}
					break;
				}
			}
		} catch (IOException e) {
			System.err.println(e);
		}
		
		return info;
	}
	
	public Annotator setAddChr(boolean addChr) {
		prefix = addChr? "chr" : "";
		return this;
	}

	public Annotator setRequirePass(boolean require) {
		requirePass = require;
		return this;
	}

}
