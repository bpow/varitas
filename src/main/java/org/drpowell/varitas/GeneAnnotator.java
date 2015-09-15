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

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.drpowell.util.FixedKeysMapFactory;

public class GeneAnnotator extends Annotator {
	public final URL fileURL;
	public final String annotatorName;
	private final HashMap<String, Map<String, Object> > data = new HashMap<String, Map<String, Object> >();
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
			FixedKeysMapFactory<String, Object> mapFactory = new FixedKeysMapFactory<String, Object>(fieldMap.values());
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
					Map<String, Object> map = mapFactory.newMap();
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
	public VariantContext annotate(VariantContext variant) {
		String varGenes = variant.getAttributeAsString("Gene_name", null);
		if (varGenes != null) {
			VariantContextBuilder builder = new VariantContextBuilder(variant);
			for (String vg: varGenes.split(",")) {
				if (data.containsKey(vg)) {
					// FIXME - handle multiple matches
					builder.attributes(data.get(vg));
				}
			}
			variant = builder.make();
		}
		return variant;
	}

	@Override
	public Iterable<VCFInfoHeaderLine> infoLines() {
		ensureFileRead();
		LinkedList<VCFInfoHeaderLine> l = new LinkedList<VCFInfoHeaderLine>();
		for (Entry<Integer, String> field : fieldMap.entrySet()) {
			StringBuilder sb = new StringBuilder();
			int colIndex = field.getKey();
			if (headers != null && headers.length >= colIndex) {
				sb.append(headers[colIndex]).append(", ");
			}
			sb.append("column ").append(field.getKey()+1).append(" from ").append(fileURL.getFile());
			l.add(new VCFInfoHeaderLine(field.getValue(), 1, VCFHeaderLineType.String, sb.toString()));
		}
		return l;
	}
	
	@Override
	public String toString() {
		return "GeneAnnotator: " + fileURL;
	}

}
