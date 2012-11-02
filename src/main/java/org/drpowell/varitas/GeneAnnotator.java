package org.drpowell.varitas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.drpowell.vcf.VCFVariant;

public class GeneAnnotator extends Annotator {
	public final URL fileURL;
	public final String annotatorName;
	private final HashMap<String, Map<String, String> > data = new HashMap<String, Map<String, String> >();
	private int keyColumn = 0;
	private LinkedHashMap<Integer, String> fieldMap = new LinkedHashMap<Integer, String>();
	private boolean initialized = false;
	private boolean hasHeader = false;
	private String [] headers;
	
	public GeneAnnotator(String annotatorName, URL input) throws IOException {
		this.annotatorName = annotatorName;
		fileURL = input;
	}
	
	private final void ensureUninitialized() {
		if (initialized) {
			throw new IllegalStateException("Attempted to set parameter for '" + this + "' after data had been read");
		}
	}
	
	private synchronized final void ensureFileRead() {
		if (!initialized) {
			initialized = true;
			if (fieldMap.size() == 0) {
				fieldMap.put(keyColumn, annotatorName);
			}
			FixedKeysMapFactory<String, String> mapFactory = new FixedKeysMapFactory<String, String>(fieldMap.values());
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(fileURL.openStream()));
				String line = null;
				if (hasHeader) {
					if ((line = reader.readLine()) != null) {
						headers = line.split("\\t", -1);
					}
				}
				while ((line = reader.readLine()) != null) {
					String [] row = line.split("\\t", -1);
					// FIXME - use apache CSV or something else to allow for more than just tsv files
					Map<String, String> map = mapFactory.newMap();
					for (Entry<Integer, String> field : fieldMap.entrySet()) {
						map.put(field.getValue(), row[field.getKey()]);
					}
					data.put(row[keyColumn], map);
				}
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}

	public GeneAnnotator hasHeader(boolean hasHeader) {
		ensureUninitialized();
		this.hasHeader = hasHeader;
		return this;
	}
	
	public GeneAnnotator setKeyColumn(int column) {
		ensureUninitialized();
		keyColumn = column - 1;
		return this;
	}
	
	public GeneAnnotator setOutputColumns(String columns) {
		ensureUninitialized();
		fieldMap.clear();
		String [] splitColumns = columns.split(",");
		for (String column : splitColumns) {
			int eq = column.indexOf("=");
			if (eq >= 0) {
				fieldMap.put(Integer.valueOf(column.substring(0, eq))-1, column.substring(eq+1));
			} else {
				fieldMap.put(Integer.valueOf(column)-1, "col" + column);
			}
		}
		return this;
	}
	
	// FIXME - allow a different Gene_name title
	
	@Override
	public VCFVariant annotate(VCFVariant variant) {
		String varGenes = (String) variant.getInfo().get("Gene_name");
		if (varGenes != null) {
			for (String vg: varGenes.split(",")) {
				if (data.containsKey(vg)) {
					// FIXME - handle multiple matches
					for (Entry<String, String> entry : data.get(vg).entrySet()) {
						variant.getInfo().put(entry.getKey(), entry.getValue());
					}
				}
			}
		}
		return variant;
	}

	@Override
	public Iterable<String> infoLines() {
		ensureFileRead();
		LinkedList<String> l = new LinkedList<String>();
		for (Entry<Integer, String> field : fieldMap.entrySet()) {
			int colIndex = field.getKey();
			StringBuilder sb = new StringBuilder(100);
			sb.append("##INFO=<ID=").append(field.getValue()).append(",Number=1,Type=String,Description=\"");
			if (headers != null && headers.length >= colIndex) {
				sb.append(headers[colIndex]).append(", ");
			}
			sb.append("column ").append(field.getKey()+1).append(" from ").append(fileURL.getFile()).append("\">");
			l.add(sb.toString());
		}
		return l;
	}
	
	@Override
	public String toString() {
		return "GeneAnnotator: " + fileURL;
	}

}
