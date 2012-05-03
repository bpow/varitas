package org.drpowell.grandannotator;

import java.util.LinkedHashMap;
import java.util.Map;



public class VCFMeta {
	// FIXME-- really only designed for INFO and FORMAT fields with their defined keys for now
	public enum MetaType {Integer, Float, Character, String, Flag};
	private String metaType;
	public String getMetaType() {
		return metaType;
	}

	public void setMetaType(String metaType) {
		this.metaType = metaType;
	}
	
	public String getValue(String key) {
		if (values != null) return values.get(key);
		return null;
	}

	private LinkedHashMap<String, String> values;
	private String singleValue;

	private VCFMeta(String typeAlone) {
		metaType = typeAlone;
	}
	
	private VCFMeta(String metaType, String value) {
		this.metaType = metaType;
		if (value.startsWith("<") && value.endsWith(">")) {
			values = parseMultipleValues(value.substring(1, value.length()-1));
		} else {
			singleValue = value;
		}
	}
	
	public VCFMeta(String metaType, LinkedHashMap<String, String> values) {
		this.metaType = metaType;
		this.values = values;
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
		return result;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder("##").append(metaType);
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

	public static VCFMeta fromLine(String line) {
		if (line.startsWith("##")) {
			line = line.substring(2);
		}
		int eq = line.indexOf("=");
		if (eq <= 0) {
			return new VCFMeta(line);
		}
		String metaType = line.substring(0, eq);
		String remainder = line.substring(eq+1);
		return new VCFMeta(metaType, remainder);
	}

	public String putValue(String key, String value) {
		return values.put(key, value);
	}
}
