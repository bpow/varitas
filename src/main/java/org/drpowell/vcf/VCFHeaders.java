package org.drpowell.vcf;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VCFHeaders extends AbstractList<VCFMeta> {
	private ArrayList<VCFMeta> headers;
	private LinkedHashMap<String, VCFMeta> infos = new LinkedHashMap<String, VCFMeta>();
	private LinkedHashMap<String, VCFMeta> formats = new LinkedHashMap<String, VCFMeta>();
	private String [] samples;
	
	public VCFHeaders(ArrayList<VCFMeta> headerList, String[] samples) {
		headers = new ArrayList<VCFMeta>(headerList.size());
		addAll(headerList);
		this.samples = samples;
	}

	public List<String> getSamples() {
		return Arrays.asList(samples);
	}
	
	public Map<String, VCFMeta> infos() {
		return Collections.unmodifiableMap(infos);
	}

	public Map<String, VCFMeta> formats() {
		return Collections.unmodifiableMap(formats);
	}
	
	@Override
	public boolean add(VCFMeta m) {
		if ("INFO".equals(m.getMetaKey())) {
			infos.put(m.getId(), m);
		} else if ("FORMAT".equals(m.getMetaKey())) {
			formats.put(m.getId(), m);
		}
		return headers.add(m);
	}
	
	@Override
	public VCFMeta get(int index) {
		return headers.get(index);
	}

	@Override
	public int size() {
		return headers.size();
	}

	public String getColumnHeaderLine() {
		StringBuffer sb = new StringBuffer(VCFParser.VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS);
		for (String sample : samples) {
			sb.append("\t").append(sample);
		}
		return sb.toString();
	}
	
	public String toString() {
		String newline = String.format("%n");
		StringBuilder sb = new StringBuilder(4096);
		for (VCFMeta m : headers) {
			sb.append(m.toString()).append(newline);
		}
		return sb.toString();
	}
	
	public String dataHeader() {
		StringBuilder sb = new StringBuilder(VCFParser.VCFFixedColumns.VCF_FIXED_COLUMN_HEADERS);
		for (String s : samples) {
			sb.append(s).append("\t");
		}
		return sb.append(String.format("%n")).toString();
	}

}
