package org.drpowell.grandannotator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class VCFVariant extends GenomicVariant {
	public String id;
	public String filter;
	private HashMap<String, String> info;
	private String qual;
	public String format;
	
	public enum FIXED_COLUMNS {CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO, FORMAT};

	private VCFVariant(String sequence, int start, String ref, String alt) {
		super(sequence, start, ref, alt);
	}
	
	public static VCFVariant createFromLine(String line) {
		String [] row = line.split("\t");
		String sequence = row[FIXED_COLUMNS.CHROM.ordinal()];
		int start = Integer.parseInt(row[FIXED_COLUMNS.POS.ordinal()]);
		String ref = row[FIXED_COLUMNS.REF.ordinal()];
		String alt = row[FIXED_COLUMNS.ALT.ordinal()];
		VCFVariant v = new VCFVariant(sequence, start, ref, alt);
		v.qual = row[FIXED_COLUMNS.QUAL.ordinal()];
		v.filter = row[FIXED_COLUMNS.FILTER.ordinal()];
		v.format = row[FIXED_COLUMNS.FORMAT.ordinal()];
		v.info = splitInfoField(row[FIXED_COLUMNS.INFO.ordinal()]);
		return v;
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

	public static String joinInfo(HashMap<String, String> info) {
		StringBuilder sb = new StringBuilder();
		for (Entry<String, String> e: info.entrySet()) {
			sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
		}
		return sb.substring(0, sb.length()-1); // no need for the last semicolon
	}

	public Map<String, String> getInfo() {
		return info;
	}
	
	public Double getQual() {
		return Double.valueOf(qual);
	}
}
