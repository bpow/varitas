package org.drpowell.grandannotator;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.broad.tribble.readers.TabixReader;

public class TabixVCFAnnotator {
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
	
	public Map<String, Object> annotate(VCFVariant var) {
		return annotate(var.getSequence(), var.getStart(), var.getEnd(), var.getRef(), var.getAlt(), var.getInfo());
	}
	
	public Map<String, Object> annotate(final String chromosome, final int start, final int end, final String ref, final String alt, Map<String, Object> info) {
		Integer tid = tabix.mChr2tid.get(prefix + chromosome);
		if (tid == null) {
			// may want to log this...
			return info;
		}
		String resultLine;
		// when using this query form, tabix expects space-based (0-based) coordinates
		TabixReader.Iterator iterator = tabix.query(tid, start-1, end);
		if (iterator == null) {
			return info;
		}
		try {
			while ((resultLine = iterator.next()) != null) {
				VCFVariant target = new VCFVariant(resultLine);
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
	
	public TabixVCFAnnotator setAddChr(boolean addChr) {
		prefix = addChr? "chr" : "";
		return this;
	}

	public TabixVCFAnnotator setRequirePass(boolean require) {
		requirePass = require;
		return this;
	}

}
