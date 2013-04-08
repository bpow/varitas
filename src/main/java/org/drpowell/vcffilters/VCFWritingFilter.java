package org.drpowell.vcffilters;

import java.io.PrintWriter;

import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFVariant;

public class VCFWritingFilter implements VariantOutput {
	private final VCFIterator variants;
	private final PrintWriter writer;
	
	public VCFWritingFilter(VCFIterator variants, PrintWriter pw) {
		this.variants = variants;
		writer = pw;
	}
	
	@Override
	public VCFHeaders getHeaders() {
		return variants.getHeaders();
	}

	@Override
	public boolean hasNext() {
		return variants.hasNext();
	}

	@Override
	public VCFVariant next() {
		VCFVariant next = variants.next();
		writer.println(next);
		return next;
	}

	@Override
	public void remove() {
		variants.remove();
	}

}
