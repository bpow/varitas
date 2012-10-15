package org.drpowell.vcf;

import java.util.LinkedHashMap;
import java.util.Map;



/**
 * A class to hold meta-information from a Variant Call Format (VCF) file.
 * 
 * For the most part, meta lines are formatted as ##KEY=value where value can
 * be a group of comma-delimited values enclosed by <>. The VCF specification
 * sets other requirements for INFO and FORMAT fields, which are not incorporated
 * into this class (yet?).
 * 
 * 
 * @see http://www.1000genomes.org/wiki/Analysis/Variant%20Call%20Format/vcf-variant-call-format-version-41
 * @author bpow
 */
public class VCFMeta {

	public enum VCFFixedColumns {
		CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO, FORMAT;
		public static final String VCF_FIXED_COLUMN_HEADERS =
				"#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT";
		public static final int SIZE = values().length;
	}

	private String metaKey;
	private LinkedHashMap<String, String> values = null;
	private String singleValue = null;
	private String id = null;
	
	public String getMetaKey() {
		return metaKey;
	}
	
	public VCFMeta setMetaKey(String key) {
		metaKey = key;
		return this;
	}
	
	/**
	 * 
	 * @param key
	 * @return
	 */
	public String getValue(String key) {
		if (values != null) return values.get(key);
		return singleValue;
	}

	private VCFMeta(String keyOnly) {
		this.metaKey = keyOnly;
		values = null;
	}
	
	private VCFMeta(String metaKey, String value) {
		this.metaKey = metaKey;
		if (value.startsWith("<") && value.endsWith(">")) {
			values = parseMultipleValues(value.substring(1, value.length()-1));
		} else {
			singleValue = value;
		}
	}
	
	/**
	 * 
	 * @param values
	 */
	public VCFMeta(String metaKey, LinkedHashMap<String, String> values) {
		this.metaKey = metaKey;
		this.values = values;
		id = values.get("ID");
	}
	
	private LinkedHashMap<String, String> parseMultipleValues(String multiValues) {
		String [] keyvalues = multiValues.split(",");
		LinkedHashMap<String, String> result = new LinkedHashMap<String, String>(keyvalues.length * 5 % 4);
		String prev = null;
		for (String s : keyvalues) {
			if (prev != null) {
				s = prev + "," + s;
				prev = null;
			}
			if ((s.indexOf('"') >= 0 ) && ((s.endsWith("\\\"") || !s.endsWith("\"")))) {
				prev = s;
			} else {
				int eq = s.indexOf("=");
				if (eq > 0) {
					result.put(s.substring(0, eq), s.substring(eq+1));
				} else {
					result.put(s, "");
				}
			}
		}
		id = result.get("ID");
		return result;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder("##").append(metaKey);
		if (values != null) {
			sb.append("=<");
			for (Map.Entry<String, String> entry : values.entrySet()) {
				sb.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
			}
			sb.setCharAt(sb.length()-1, '>');
		} else if (singleValue != null) {
			sb.append("=").append(singleValue);
		}
		return sb.toString();
	}

	/**
	 * 
	 * @param line
	 * @return
	 */
	public static VCFMeta fromLine(String line) {
		if (line.startsWith("##")) {
			line = line.substring(2);
		}
		int eq = line.indexOf("=");
		if (eq <= 0) {
			return new VCFMeta(line);
		}
		String metaKey = line.substring(0, eq);
		String remainder = line.substring(eq+1);
		return new VCFMeta(metaKey, remainder);
	}

	/**
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public String putValue(String key, String value) {
		if ("ID".equals(key)) {
			id = key;
		}
		return values.put(key, value);
	}
	
	public String getId() {
		return id;
	}
}
