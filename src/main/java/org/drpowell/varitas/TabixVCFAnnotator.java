package org.drpowell.varitas;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class TabixVCFAnnotator extends Annotator {
	private final VCFFileReader reader;
	private final Map<String, String> fieldMap = new LinkedHashMap<String, String>();
	private boolean requirePass;
	private boolean copyID = false;

	public static final String stringJoin(String delimiter, String[] strings) {
		StringBuilder sb = new StringBuilder();
		for (String string : strings) {
			sb.append(string).append(delimiter);
		}
		return sb.substring(0, sb.length() - delimiter.length());
	}
	
	public TabixVCFAnnotator(final VCFFileReader reader, final Map<String, String> fields) {
		this.reader = reader;
		fieldMap.putAll(fields);
	}
	
	public TabixVCFAnnotator(final VCFFileReader reader, String fieldString) {
		this.reader = reader;
		String [] fields = fieldString.split(",");
		for (String field : fields) {
			int eq = field.indexOf("=");
			if (eq < 0) {
				fieldMap.put(field, field);
			} else {
				fieldMap.put(field.substring(0, eq), field.substring(eq+1));
			}
		}
	}
		
	@Override
	public VariantContext annotate(VariantContext variant) {
		int start = variant.getStart();
		int end = variant.getEnd();
		Allele ref = variant.getReference();
		Allele alt = variant.getAltAlleleWithHighestAlleleCount(); // FIXME -- handle multiple alleles
		VariantContext target;
		Iterator<VariantContext> iterator = reader.query(variant.getContig(), start, end);
		while ((target = iterator.next()) != null) {
			if (target.getStart() == start &&
					target.getReference().equals(ref) &&
					target.getAltAlleleWithHighestAlleleCount().equals(alt)) {
				// FIXME - some target files will have more than one variant per line
				if (requirePass && !target.getFilters().isEmpty()) {
					continue;
				}
				// found a match!
				VariantContextBuilder builder = new VariantContextBuilder(variant);
				for (Entry<String, String> e : fieldMap.entrySet()) {
					if (target.hasAttribute(e.getKey())) {
						// FIXME- should check to prevent duplicates being overwritten
						builder.attribute(e.getValue(), target.getAttribute(e.getKey()));
					}
					if (copyID) {
						if (variant.emptyID() && !target.emptyID())
							builder.id(target.getID());
					}
					variant = builder.make();
					break;
				}
			}
		}
		return variant;
	}
	
	public Annotator setRequirePass(boolean require) {
		requirePass = require;
		return this;
	}
	
	public Annotator setCopyID(boolean copyID) {
		this.copyID  = copyID;
		return this;
	}

	@Override
	public Iterable<VCFInfoHeaderLine> infoLines() {
		ArrayList<VCFInfoHeaderLine> infos = new ArrayList<VCFInfoHeaderLine>();
		HashMap<String, String> newInfos = new HashMap<String, String>();
		VCFHeader readerHeader = reader.getFileHeader();
		String newId = null;
		for (VCFInfoHeaderLine headerLine : readerHeader.getInfoHeaderLines()) {
			String oldId = headerLine.getID();
			if ((newId = fieldMap.get(oldId)) != null) {
				infos.add(newId == oldId ? headerLine :
								new VCFInfoHeaderLine(newId, headerLine.getCountType(),
										headerLine.getType(), headerLine.getDescription())
				);
			}
		}
		return infos;
	}

}
