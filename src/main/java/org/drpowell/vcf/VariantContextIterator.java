package org.drpowell.vcf;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;

import java.util.Iterator;

public interface VariantContextIterator extends CloseableIterator<VariantContext> {

	public VCFHeader getHeader();

}
