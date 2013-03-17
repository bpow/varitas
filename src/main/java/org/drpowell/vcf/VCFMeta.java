package org.drpowell.vcf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;



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

	private final String metaKey;
	private final String value;
	private final LinkedHashMap<String, String> splitValues;
	
	public String getMetaKey() {
		return metaKey;
	}

	public String getValue(String key) {
		if (splitValues != null) return splitValues.get(key);
		// FIXME maybe return null
		throw new NoSuchElementException(String.format("Tried to access %s in header line: %s", key, value));
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("##").append(metaKey);
		if (value != null) {
			sb.append("=").append(value);
		}
		return sb.toString();
	}
	
	public String getId() {
		return splitValues.get("ID");
	}

	/**
	 * Standard constructor - will parse a line from a VCF file and split it appropriately
	 * @param line - according to VCF specification should start with '##'
	 */
	public VCFMeta(String line) {
		if (line.startsWith("##")) {
			line = line.substring(2);
		}
		int eq = line.indexOf("=");
		if (eq <= 0) {
			metaKey = line;
			value = null;
			splitValues = null;
		} else {
			metaKey = line.substring(0, eq);
			value = line.substring(eq+1);
			if (value.startsWith("<") && value.endsWith(">")) {
				splitValues = parseMultipleMetaValues(value.substring(1, value.length()-1));
			} else {
				splitValues = null;
			}
		}
	}
	
	/**
	 * Constructor for a line where a key points to a dictionary (like INFO and FORMAT lines)
	 */
	public VCFMeta(String metaKey, LinkedHashMap<String, String> values) {
		this.metaKey = metaKey;
		if (values == null || values.isEmpty()) {
			value = null;
			splitValues = null;
		} else {
			splitValues = values; // WARNING - consider protective copy
			value = headerValueFromMap(splitValues);
		}
	}
	
	/**
	 * Make a new VCFMeta based on this, but with one of the fields in its dictionary changed.
	 * 
	 * This would be useful, for instance, in changing the ID of a VCFMeta.
	 * 
	 * @throws IllegalArgumentException if this VCFMeta does not already have dictionary-type value
	 * 
	 * @param key
	 * @param value
	 * @return a new VCFMeta
	 */
	public final VCFMeta cloneExcept(String key, String value) {
		if (splitValues == null) {
			throw new IllegalArgumentException("Cannot call VCFMeta.cloneExcept on a VCFMeta that does not have a dictionary-type value: " + this.value);
		}
		LinkedHashMap<String, String> newValues = new LinkedHashMap<String, String>(splitValues);
		newValues.put(key, value);
		return new VCFMeta(metaKey, newValues);
	}

	private static String headerValueFromMap(LinkedHashMap<String, String> values) {
		StringBuilder sb = new StringBuilder().append("<");
		for (Map.Entry<String, String> kv : values.entrySet()) {
			sb.append(kv.getKey()).append("=").append(kv.getValue()).append(",");
		}
		if (sb.charAt(sb.length() - 1) == ',') {
			sb.deleteCharAt(sb.length() - 1);
		}
		sb.append('>');
		return sb.toString();
	}
		
	private static LinkedHashMap<String, String> parseMultipleMetaValues(String multiValues) {
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
		return result;
	}
	
}
