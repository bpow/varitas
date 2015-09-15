package org.drpowell.vcffilters;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import org.drpowell.util.FilteringIterator;
import org.drpowell.vcf.VariantContextIterator;

public abstract class VariantContextFilteringIterator extends FilteringIterator<VariantContext> implements VariantContextIterator {
	protected VCFHeader originalHeader;

	public VariantContextFilteringIterator(CloseableIterator<VariantContext> client, VCFHeader header) {
		super(client);
		this.setOriginalHeader(header);
	}
	
	public VariantContextFilteringIterator(VariantContextIterator client) {
		this(client, client.getHeader());
	}

	protected void setOriginalHeader(VCFHeader header) {
		originalHeader = header;
	}
	
	public VCFHeader getHeader() {
		return originalHeader;
	}

}
