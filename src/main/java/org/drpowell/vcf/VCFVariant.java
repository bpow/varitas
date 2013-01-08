package org.drpowell.vcf;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import java.util.logging.Logger;

/**
 * Representation of a single row of a VCF file
 * 
 * INFO flag fields will have a 'null' value if set
 * 
 * @author bpow
 */
public class VCFVariant {
	private Map<String, String[]> info;
	private String qual;
	private String [] row;
	private int start; // fixme should this be final?
	private int end;
	private boolean urlEncode = true;
	private volatile double [][] logLikelihoods;
	private static final String URL_ENCODING = "UTF-8";
	private static final String [] FLAG_INFO = new String[0];
	
	public VCFVariant(String line) {
		this(line.split("\t", -1));
	}
	
	public VCFVariant(String [] row) {
		this.row = row; // FIXME - should defensive copy?
		start = Integer.parseInt(row[VCFParser.VCFFixedColumns.POS.ordinal()]);
		end = start + getRef().length() - 1;
		info = splitInfoField(row[VCFParser.VCFFixedColumns.INFO.ordinal()]);
	}

	public static Map<String, String[]> splitInfoField(String info) {
		Map<String, String[]> map = new LinkedHashMap<String, String[]>();
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
				map.put(keyvalue[0], FLAG_INFO);
			} else {
				map.put(keyvalue[0], keyvalue[1].split(","));
			}
		}
		return map;
	}

	public static String joinInfo(Map<String, String []> info) {
		if (info.size() == 0) {
			return ".";
		}
		StringBuilder sb = new StringBuilder();
		for (Entry<String, String[]> e: info.entrySet()) {
			if (e.getValue() == FLAG_INFO) {
				sb.append(e.getKey()).append(";");
			} else {
				sb.append(e.getKey()).append("=").append(join(",",decodeInfo(false, e.getValue()))).append(";");
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
	public VCFVariant putInfo(String key, String... values) {
		if (null != values) {
			values = encodeInfo(urlEncode, values);
		} else {
			values = FLAG_INFO;
		}
		info.put(key, values);
		return this;
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
	
	private final int findFormatItemIndex(String key) {
		String [] format = getFormat().split(":");
		for (int i = 0; i < format.length; i++) {
			if (key.equals(format[i])) return i;
		}
		return -1;
	}
	
	public static int[] PLfromGL(double [] GLs) {
	    final int[] pls = new int[GLs.length];
	    int min = 255;
	    for ( int i = 0; i < GLs.length; i++ ) {
	        pls[i] = Math.min((int) Math.round(-10 * GLs[i]), 255);
	        min = Math.min(pls[i], min);
	    }
	    for ( int i = 0; i < GLs.length; i++ ) {
	    	pls[i] -= min;
	    }
	    return pls;
	}
	
	private double [][] extractLikelihoods() {
		// i indexes sample, j indexes individual likelihood
		boolean foundGL = false;
		int index = findFormatItemIndex("GL");
		if (index >= 0) {
			foundGL = true;
		} else {
			index = findFormatItemIndex("PL");
			if (index < 0) {
				// didn't find GL or PL... but if we were to return 'null', someone might try again
				return new double[0][0];
			}
		}
		String [] calls = getCalls();
		double [][] res = new double[calls.length][];
		for (int i = 0; i < res.length; i++) {
			String [] callFields = calls[i].split(":");
			if (index >= callFields.length) {
				// no call for this sample
				res[i] = null;
			} else {
				res[i] = VCFUtils.parseDoubleList(callFields[index]);
				if (!foundGL) {
					for (int j = 0; j < res[i].length; j++) {
						res[i][j] /= -10.0;
					}
				}
			}
		}
		return res;
	}
	
	public double [][] getGenotypeLikelihoods() {
		double [][] result = logLikelihoods;
		if (null == result) {
			synchronized(this) {
				result = logLikelihoods;
				if (null == result) {
					result = logLikelihoods = extractLikelihoods();
				}
			}
		}
		if (result.length == 0) return null;
		return result;
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
	
	public String [] getInfoValues(boolean urlDecode, String key) {
		return decodeInfo(urlDecode, info.get(key));
	}

	public String getInfoValue(String key) {
		return getInfoValue(key, true);
	}
	
	/**
	 * Return the value within the INFO dictionary for a given key, optionally performing urlDecoding
	 * 
	 * @param key
	 * @return null if key not present, "" for flag fields, the encoded value otherwise
	 */
	public String getInfoValue(String key, boolean urlDecode) {
		String [] vals = info.get(key);
		if (vals == FLAG_INFO) return "";
		if (vals == null) return null;
		vals = decodeInfo(urlDecode, vals);
		return join(",", vals);
	}

	public boolean hasInfo(String key) {
		return info.containsKey(key);
	}
	
	public static final String [] decodeInfo(boolean urlDecode, String... values) {
		if (urlDecode) {
			String [] decoded = new String[values.length];
			for (int i = 0; i < values.length; i++) {
				try {
					decoded[i] = URLDecoder.decode(values[i], URL_ENCODING);
				} catch (UnsupportedEncodingException e) {
					Logger logger = Logger.getLogger("VARITAS");
					logger.warning("Unable to URL-decode the string: '" + values[i] + "', it will be returned as-is.\n" + e.getMessage());
					decoded[i] = values[i];
				}
			}
			values = decoded;
		}
		return values;
	}
	
	public static final String [] encodeInfo(boolean urlEncode, String... values) {
		if (urlEncode) {
			String [] encoded = new String[values.length];
			for (int i = 0; i < values.length; i++) {
				try {
					encoded[i] = URLEncoder.encode(values[i], URL_ENCODING);
				} catch (UnsupportedEncodingException e) {
					Logger logger = Logger.getLogger("VARITAS");
					logger.warning("Unable to URL-encode the string: '" + values[i] + "', it will be returned as-is.\n" + e.getMessage());
					encoded[i] = values[i];
				}
			}
			values = encoded;
		}
		return values;
	}

	private static String join(String sep, String... strings) {
		if (strings.length == 0) return "";
		if (strings.length == 1) return strings[0];
		StringBuilder sb = new StringBuilder();
		for (String s: strings) {
			sb.append(",").append(s);
		}
		return sb.substring(1);
	}
}
