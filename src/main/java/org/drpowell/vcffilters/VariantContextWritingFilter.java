package org.drpowell.vcffilters;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFHeader;
import org.drpowell.vcf.VariantContextIterator;

import java.io.OutputStream;
import java.io.PrintWriter;

public class VariantContextWritingFilter implements VariantOutput {
	private final VariantContextIterator variants;
	private final VariantContextWriter writer;
	private boolean stillOpen = true;
	private final VCFHeader header;

	public VariantContextWritingFilter(VariantContextIterator variants, OutputStream os) {
		this.variants = variants;
		header = variants.getHeader();
		writer = new VariantContextWriterBuilder()
				.setReferenceDictionary(header.getSequenceDictionary())
				.setOption(Options.INDEX_ON_THE_FLY)
				.setBuffer(8192)
				.setOutputStream(os)
				.build();
		writer.writeHeader(header);
	}
	
	@Override
	public VCFHeader getHeader() {
		return header;
	}

	@Override
	public boolean hasNext() {
		boolean hasNext = variants.hasNext();
		if (!hasNext && stillOpen) {
			writer.close();
		}
		return hasNext;
	}

	@Override
	public VariantContext next() {
		VariantContext next = variants.next();
		writer.add(next);
		return next;
	}

	@Override
	public void remove() {
		variants.remove();
	}

	@Override
	public void close() {
		writer.close();
	}
}
