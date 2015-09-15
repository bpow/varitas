package org.drpowell.vcffilters;

import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypesContext;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.drpowell.vcf.VariantContextIterator;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A file that wraps a VariantContextIterator to write the variants to a .TSV file.
 * 
 * TSVWritingFilter itself implements VariantContextIterator, so it can be used as part of a chain of filters.
 *
 * WARNING: the current implementation closes (and thus flushes) the output stream when the client
 * iterator is depleted (i.e. when hasNext() returns false), so it is important to process through
 * all of the variants of the iterator.
 * 
 * @author bpow
 *
 */
public class TSVWritingFilter implements VariantOutput {
	private final VariantContextIterator variants;
	private final PrintWriter pw;
	private final Collection<String> formats;
	private final Collection<VCFInfoHeaderLine> infos;
	private final List<String> samples;

	// this is really hacky-- most VCF files tend to have the FORMAT header lines in alphabetical
	// order, which is really obnoxious for viewing. I like this order better...
	private static final String[] PREFERRED_FORMAT_ORDER = {
		"GT", "AD", "DP", "DV", "RR", "VR", "GQ", "PL", "GL"
	};
	
	/**
	 * Wrap the variants VariantContextIterator, planning to write output to the specified OutputStream
	 * 
	 * @param variants
	 * @param os
	 */
	public TSVWritingFilter(VariantContextIterator variants, OutputStream os) {
		this.variants = variants;
		this.pw = new PrintWriter(os);
		VCFHeader vcfHeaders = variants.getHeader();
		Collection<VCFFormatHeaderLine> formatLines = vcfHeaders.getFormatHeaderLines();
		// make the formats LinkedHashSet in a special order
		formats = new LinkedHashSet<String>(formatLines.size()*3/2, 0.75f);
		for (String fkey : PREFERRED_FORMAT_ORDER) {
			if (vcfHeaders.hasFormatLine(fkey)) {
				formats.add(fkey);
			}
		}
		for (VCFFormatHeaderLine formatLine : formatLines) {
			formats.add(formatLine.getID());
		}
		samples = vcfHeaders.getSampleNamesInOrder();
		infos = vcfHeaders.getInfoHeaderLines();
		pw.println(headerLine());
	}
	
	private String headerLine() {
		StringBuilder sb = new StringBuilder(VCFHeader.METADATA_INDICATOR);
		VCFHeader header = getHeader();
		for (final VCFHeader.HEADER_FIELDS field : header.getHeaderFields()) {
			sb.append(field).append(VCFConstants.FIELD_SEPARATOR);
		}
		for (VCFInfoHeaderLine infoLine : header.getInfoHeaderLines()) {
			sb.append(infoLine.getID()).append(VCFConstants.FIELD_SEPARATOR);
		}
		sb.append(String.join(VCFConstants.FIELD_SEPARATOR, header.getSampleNamesInOrder()));
		return sb.toString();
	}
	
	private VariantContext writeRow(VariantContext v) {
		StringBuilder sb = new StringBuilder(v.toString());
		for (VCFInfoHeaderLine i : infos) {
			Object value = v.getAttribute(i.getID());
			if ("".equals(value)) {
				value = i; // flag fields should display as something.
			} else if (value == null) {
				value = "";
			}
			sb.append("\t").append(value); // FIXME -- lists!
		}
		GenotypesContext calls = v.getGenotypes();
		if (calls.size() == samples.size()) {
			for (Genotype call: calls) {
				for (String f : formats) {
					Object val = call.getAnyAttribute(f);
					sb.append("\t").append(val == null ? "" : val);
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
	public VariantContext next() {
		return writeRow(variants.next());
	}

	@Override
	public void remove() {
		variants.remove();
	}

	@Override
	public VCFHeader getHeader() {
		return variants.getHeader();
	}

	@Override
	public void close() {
		// FIXME -- Do I need to do anything here?
	}
}
