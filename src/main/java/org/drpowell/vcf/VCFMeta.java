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
	
	public String getValue(String key) {
		if (values != null) return values.get(key);
		return singleValue;
	}

	public VCFMeta(String metaKey, String value) {
		this.metaKey = metaKey;
		singleValue = value;
	}
	
	public VCFMeta(String metaKey, LinkedHashMap<String, String> values) {
		this.metaKey = metaKey;
		this.values = values;
		id = values.get("ID");
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
