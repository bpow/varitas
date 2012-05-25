package org.drpowell.grandannotator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class VCFVariant implements GenomicVariant {
	private Map<String, Object> info;
	private String qual;
	public String format;
	private String [] row;
	private int start; // fixme should this be final?
	private int end;
	
	public VCFVariant(String line) {
		this(line.split("\t"));
	}
	
	public VCFVariant(String [] row) {
		this.row = row; // FIXME - should defensive copy?
		start = Integer.parseInt(row[VCFFixedColumns.POS.ordinal()]);
		end = start + getRef().length() - 1;
		info = splitInfoField(row[VCFFixedColumns.INFO.ordinal()]);
	}

	public static Map<String, Object> splitInfoField(String info) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
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

	public Map<String, Object> getInfo() {
		return info;
	}
	
	public Double getQual() {
		return Double.valueOf(qual);
	}
	
	private void updateInfo() {
		row[VCFFixedColumns.INFO.ordinal()] = joinInfo(info);
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
		return row[VCFFixedColumns.CHROM.ordinal()];
	}

	@Override
	public int getStart() {
		return start;
	}

	@Override
	public int getEnd() {
		return end;
	}

	public String getID() {
		return row[VCFFixedColumns.ID.ordinal()];
	}

	@Override
	public String getRef() {
		return row[VCFFixedColumns.REF.ordinal()];
	}

	@Override
	public String getAlt() {
		return row[VCFFixedColumns.ALT.ordinal()];
	}
	
	public String getFilter() {
		return row[VCFFixedColumns.FILTER.ordinal()];
	}
	
	public String getFormat() {
		return row[VCFFixedColumns.FORMAT.ordinal()];		
	}
	
	public VCFVariant mergeID(String newID) {
		int idcol = VCFFixedColumns.ID.ordinal();
		String oldID = row[idcol];
		if (!".".equals(oldID)) {
			if (oldID.equals(newID)) {
				return this;
			}
			// should probably log this -- changing a previously-written rsID
		}
		row[idcol] = newID;
		return this;
	}
}
