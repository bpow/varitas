package org.drpowell.grandannotator;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.broad.tribble.readers.TabixIteratorLineReader;
import org.broad.tribble.readers.TabixReader;

public class TabixVCFAnnotator {
	private final TabixReader tabix;
	private final HashMap<String, String> fieldMap = new HashMap<String, String>();
	private String prefix = ""; // can be "chr" if we need to add a prefix for query purposes
	private boolean requirePass;

	public static final String stringJoin(String delimiter, String[] strings) {
		StringBuilder sb = new StringBuilder();
		for (String string : strings) {
			sb.append(string).append(delimiter);
		}
		return sb.substring(0, sb.length() - delimiter.length());
	}
			
	public TabixVCFAnnotator(final TabixReader reader, final List<String> fields) {
		tabix = reader;
		for (String field : fields) {
			fieldMap.put(field, field);
		}
	}
	
	public TabixVCFAnnotator(final TabixReader reader, final Map<String, String> fields) {
		tabix = reader;
		fieldMap.putAll(fields);
	}
	
	public HashMap<String, String> annotate(final String chromosome, final int start, final int end, final String ref, final String alt, HashMap<String, String> info) {
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
				String [] targetRow = resultLine.split("\t");
				// check on position (1), ref (3) and alt (4)
				if (Integer.parseInt(targetRow[1]) == start && targetRow[3].equals(ref) && targetRow[4].equals(alt)) {
					if (requirePass && targetRow[6] != "PASS") {
						continue;
					}
					// found a match!
					HashMap<String, String> targetInfo = splitInfoField(targetRow[7]);
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
	
	public String annotateLine(String [] row) {
		String info = row[7];
		HashMap<String, String> queryInfoMap = splitInfoField(info);
		String query = prefix + row[0] + ":" + row[1] + "-" + row[1]; // I just search at the start position and check the ref,alt values
		TabixIteratorLineReader lineReader = new TabixIteratorLineReader(tabix.query(query));
		String line;
		try {
			while ((line = lineReader.readLine()) != null) {
				String [] targetRow = line.split("\t");
				// check on position (1), ref (3) and alt (4)
				if (targetRow[1].equals(row[1]) && targetRow[3].equals(row[3]) && targetRow[4].equals(row[4])) {
					// found a match!
					HashMap<String, String> targetInfo = splitInfoField(targetRow[7]);
					for (Entry<String, String> e: fieldMap.entrySet()) {
						if (targetInfo.containsKey(e.getKey())) {
							// FIXME- should check to prevent duplicates being overwritten
							queryInfoMap.put(e.getValue(), targetInfo.get(e.getKey()));
						}
					}
				}
			}
		} catch (IOException e) {
			System.err.println(e);
		}
		row[7] = joinInfo(queryInfoMap);
		return stringJoin("\t", row);
	}

	public static String joinInfo(HashMap<String, String> info) {
		StringBuilder sb = new StringBuilder();
		for (Entry<String, String> e: info.entrySet()) {
			sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
		}
		return sb.substring(0, sb.length()-1); // no need for the last semicolon
	}

	public static HashMap<String, String> splitInfoField(String info) {
		HashMap<String, String> map = new LinkedHashMap<String, String>();
		String [] entries = info.split(";");
		for (String entry : entries) {
			String [] keyvalue = entry.split("=",2);
			if (keyvalue.length == 1) {
				// Flag field, has no value
				String tmp = keyvalue[0];
				keyvalue = new String[2];
				keyvalue[0] = tmp;
				keyvalue[1] = ""; // or could use null
			}
			if (map.containsKey(keyvalue[0])) {
				throw new RuntimeException("Unable to deal with duplicated keys in the INFO field of a VCF");
			}
			map.put(keyvalue[0], keyvalue[1]);
		}
		return map;
	}

	public void setAddChr(boolean addChr) {
		prefix = addChr? "chr" : "";
	}

	public void setRequirePass(boolean require) {
		requirePass = require;
	}

}
