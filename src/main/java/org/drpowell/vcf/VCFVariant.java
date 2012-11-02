package org.drpowell.vcf;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

/**
 * Representation of a single row of a VCF file
 * 
 * @author bpow
 */
public class VCFVariant {
	private Map<String, Object> info;
	private String qual;
	private String [] row;
	private int start; // fixme should this be final?
	private int end;
	private static final Boolean INFO_FLAG_TRUE = new Boolean(true);
	
	public VCFVariant(String line) {
		this(line.split("\t", -1));
	}
	
	public VCFVariant(String [] row) {
		this.row = row; // FIXME - should defensive copy?
		start = Integer.parseInt(row[VCFParser.VCFFixedColumns.POS.ordinal()]);
		end = start + getRef().length() - 1;
		info = splitInfoField(row[VCFParser.VCFFixedColumns.INFO.ordinal()]);
	}

	public static Map<String, Object> splitInfoField(String info) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		if (".".equals(info)) {
			return map;
		}
		String [] entries = info.split(";");
		for (String entry : entries) {
			String [] keyvalue = entry.split("=",2);
			if (map.containsKey(keyvalue[0])) {
				throw new RuntimeException("Unable to deal with duplicated keys in the INFO field of a VCF");
			}
			if (keyvalue.length == 1) {
				map.put(keyvalue[0], INFO_FLAG_TRUE);
			} else {
				map.put(keyvalue[0], keyvalue[1]);
			}
		}
		return map;
	}

	public static String joinInfo(Map<String, Object> info) {
		if (info.size() == 0) {
			return ".";
		}
		StringBuilder sb = new StringBuilder();
		for (Entry<String, Object> e: info.entrySet()) {
			if (e.getValue() == INFO_FLAG_TRUE) {
				sb.append(e.getKey()).append(";");
			} else {
				sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
			}
		}
		return sb.substring(0, sb.length()-1); // no need for the last semicolon
	}
	
	/**
	 * Add an item to the VCF variant.
	 * 
	 * @param key - the ID of the data, this should be defined in the VCF header
	 * @param value - if null, then the key is treated as a FLAG field
	 * @return this VCFVariant, to facilitate chaining
	 */
	public VCFVariant putInfo(String key, Object value) {
		if (value == null) value = INFO_FLAG_TRUE;
		info.put(key, value);
		return this;
	}

	/**
	 * Returns the contents of the info field as a map. This is a reference to the actual map used
	 * in this VCFVariant, so modifications are not threadsafe and should only be undertaken if you
	 * really know what you are doing.
	 */
	public Map<String, Object> getInfo() {
		return info;
	}
	
	public Double getQual() {
		return Double.valueOf(qual);
	}
	
	private void updateInfo() {
		row[VCFParser.VCFFixedColumns.INFO.ordinal()] = joinInfo(info);
	}
	
	public String toString() {
		updateInfo();
		StringBuilder sb = new StringBuilder(row[0]);
		for (int i = 1; i < row.length; i++) {
			sb.append("\t").append(row[i]);
		}
		return sb.toString();
	}

	/**
	 * Returns the value of one of the fixed columns of a vcf file.
	 * @see VCFParser.VCFFixedColumns
	 */
	public String getFixedColumn(int i) {
		if (i >= VCFParser.VCFFixedColumns.SIZE) {
			throw new NoSuchElementException("Tried to access an invalid column in a VCF file");
		}
		return row[i];
	}

	public String getSequence() {
		return row[VCFParser.VCFFixedColumns.CHROM.ordinal()];
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}
	
	public String getID() {
		return row[VCFParser.VCFFixedColumns.ID.ordinal()];
	}

	public String getRef() {
		return row[VCFParser.VCFFixedColumns.REF.ordinal()];
	}

	public String getAlt() {
		return row[VCFParser.VCFFixedColumns.ALT.ordinal()];
	}
	
	public String getFilter() {
		return row[VCFParser.VCFFixedColumns.FILTER.ordinal()];
	}
	
	public String getFormat() {
		return row[VCFParser.VCFFixedColumns.FORMAT.ordinal()];		
	}
	
	public List<String> getRow() {
		return Collections.unmodifiableList(Arrays.asList(row));
	}
	
	public VCFVariant mergeID(String newID) {
		int idcol = VCFParser.VCFFixedColumns.ID.ordinal();
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
	
	public String [] getCalls() {
		int num = row.length - VCFParser.VCFFixedColumns.SIZE;
		if (num <= 0) {
			return new String[0];
		} else {
			return Arrays.copyOfRange(row, VCFParser.VCFFixedColumns.SIZE, row.length);
		}
	}
	
	public String getInfoField(String key) {
		Object o = info.get(key);
		if (o == null) {
			return "";
		}
		if (o == INFO_FLAG_TRUE) {
			return key;
		}
		return o.toString();
	}

	public boolean hasInfo(String key) {
		return info.containsKey(key);
	}

}
