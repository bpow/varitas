package org.drpowell.vcffilters;

import java.io.PrintWriter;

import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFVariant;

public class VCFWritingFilter implements VariantOutput {
	private final VCFIterator variants;
	private final PrintWriter writer;
	private boolean stillOpen = true;
	private final VCFHeaders headers;
	
	public VCFWritingFilter(VCFIterator variants, PrintWriter pw) {
		this.variants = variants;
		writer = pw;
		headers = variants.getHeaders();
		writer.print(headers);
		writer.println(headers.getColumnHeaderLine());
	}
	
	@Override
	public VCFHeaders getHeaders() {
		return headers;
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
