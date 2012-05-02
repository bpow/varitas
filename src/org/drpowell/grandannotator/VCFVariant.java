package org.drpowell.grandannotator;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class VCFVariant implements GenomicVariant {
	public String id;
	private ConcurrentMap<String, Object> info;
	private String qual;
	public String format;
	private String [] row;
	private int start;
	private int end;
	
	public enum FIXED_COLUMNS {CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO, FORMAT};
	
	public VCFVariant(String line) {
		this(line.split("\t"));
	}
	
	public VCFVariant(String [] row) {
		this.row = row; // FIXME - should defensive copy?
		start = Integer.parseInt(row[FIXED_COLUMNS.POS.ordinal()]);
		end = start + getRef().length() - 1;
		info = splitInfoField(row[FIXED_COLUMNS.INFO.ordinal()]);
	}

	public static ConcurrentMap<String, Object> splitInfoField(String info) {
		ConcurrentMap<String, Object> map = new ConcurrentHashMap<String, Object>();
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

	public static String joinInfo(Map<String, Object> info) {
		StringBuilder sb = new StringBuilder();
		for (Entry<String, Object> e: info.entrySet()) {
			sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
		}
		return sb.substring(0, sb.length()-1); // no need for the last semicolon
	}

	public ConcurrentMap<String, Object> getInfo() {
		return info;
	}
	
	public Double getQual() {
		return Double.valueOf(qual);
	}
	
	private void updateInfo() {
		row[FIXED_COLUMNS.INFO.ordinal()] = joinInfo(info);
	}
	
	public String toString() {
		updateInfo();
		StringBuilder sb = new StringBuilder(row[0]);
		for (int i = 1; i < row.length; i++) {
			sb.append("\t").append(row[i]);
		}
		return sb.toString();
	}

	@Override
	public String getSequence() {
		return row[FIXED_COLUMNS.CHROM.ordinal()];
	}

	@Override
	public int getStart() {
		return start;
	}

	@Override
	public int getEnd() {
		return end;
	}

	@Override
	public String getRef() {
		return row[FIXED_COLUMNS.REF.ordinal()];
	}

	@Override
	public String getAlt() {
		return row[FIXED_COLUMNS.ALT.ordinal()];
	}
	
	public String getFilter() {
		return row[FIXED_COLUMNS.FILTER.ordinal()];
	}
	
	public String getFormat() {
		return row[FIXED_COLUMNS.FORMAT.ordinal()];		
	}
}
