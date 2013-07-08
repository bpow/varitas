package org.drpowell.vcffilters;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFVariant;

/**
 * A file that wraps a VCFIterator to write the variants to a .TSV file.
 * 
 * TSVWritingFilter itself implements VCFIterator, so it can be used as part of a chain of filters.
 *
 * WARNING: the current implementation closes (and thus flushes) the output stream when the client
 * iterator is depleted (i.e. when hasNext() returns false), so it is important to process through
 * all of the variants of the iterator.
 * 
 * @author bpow
 *
 */
public class TSVWritingFilter implements VariantOutput {
	private final VCFIterator variants;
	private final PrintWriter pw;
	private Map<String, VCFMeta> formats;
	private List<String> samples;

	// this is really hacky-- most VCF files tend to have the FORMAT header lines in alphabetical
	// order, which is really obnoxious for viewing. I like this order better...
	private static final String[] PREFERRED_FORMAT_ORDER = {
		"GT", "AD", "DP", "DV", "RR", "VR", "GQ", "PL", "GL"
	};
	
	/**
	 * Wrap the variants VCFIterator, planning to write output to the specified OutputStream
	 * 
	 * @param variants
	 * @param os
	 */
	public TSVWritingFilter(VCFIterator variants, OutputStream os) {
		this.variants = variants;
		this.pw = new PrintWriter(os);
		VCFHeaders vcfHeaders = variants.getHeaders();
		Map<String, VCFMeta> headerFormats = vcfHeaders.formats();
		// make the formats LinkedHashMap in a special order
		formats = new LinkedHashMap<String, VCFMeta>(headerFormats.size()*3/2, 0.75f);
		for (String fkey : PREFERRED_FORMAT_ORDER) {
			VCFMeta tmpMeta;
			if ((tmpMeta = headerFormats.get(fkey)) != null) {
				formats.put(fkey, tmpMeta);
			}
		}
		formats.putAll(headerFormats);
		samples = vcfHeaders.getSamples();
		pw.println(headerLine());
	}
	
	private String headerLine() {
		StringBuilder sb = new StringBuilder(getHeaders().getColumnHeaderLine());
		for (VCFMeta m: getHeaders().infos().values()) {
			sb.append("\t").append(m.getId());
		}
		for (String s : samples) {
			for (VCFMeta m: formats.values()) {
				sb.append("\t").append(s + "_" + m.getId());
			}
		}
		return sb.toString();
	}
	
	private VCFVariant writeRow(VCFVariant v) {
		StringBuilder sb = new StringBuilder(v.toString());
		for (String i : variants.getHeaders().infos().keySet()) {
			String value = v.getInfoValue(i, true);
			if ("".equals(value)) {
				value = i; // flag fields should display as something.
			} else if (value == null) {
				value = "";
			}
			sb.append("\t").append(value);
		}
		String [] calls = v.getCalls();
		String [] callFormat = v.getFormat().split(":", -1);
		Map<String, Integer> formatIndices = new HashMap<String, Integer>(callFormat.length * 2);
		for (int i = 0; i < callFormat.length; i++) {
			formatIndices.put(callFormat[i], i);
		}
		if (calls.length == samples.size()) {
			for (String call: calls) {
				String [] subfields = call.split(":");
				for (String k: formats.keySet()) {
					Integer i = formatIndices.get(k);
					if (i == null || i >= subfields.length) {
						sb.append("\t").append("");
					} else {
						sb.append("\t").append(subfields[i]);
					}
				}
			}
		} else {
			throw new RuntimeException("Problem with VCF line: " + v.toString());
		}
		pw.println(sb);
		return v;
	}

	@Override
	public boolean hasNext() {
		boolean hasNext = variants.hasNext();
		if (!hasNext) {
			pw.close();
		}
		return hasNext;
	}

	@Override
	public VCFVariant next() {
		return writeRow(variants.next());
	}

	@Override
	public void remove() {
		variants.remove();
	}

	@Override
	public VCFHeaders getHeaders() {
		return variants.getHeaders();
	}

}
